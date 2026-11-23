#include <jni.h>
#include <android/log.h>
#include <llama.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>

#define TAG "PChatGGUF"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct Engine {
    llama_model * model = nullptr;
    const llama_vocab * vocab = nullptr;
    std::string tmpl;
    int threads = 4;
    std::mutex mu;
    std::atomic<bool> cancel{false};
};

extern "C" void ggml_backend_load_all_from_path(const char * dir_path);

static void throwJava(JNIEnv * env, const char * msg) {
    jclass c = env->FindClass("java/lang/IllegalStateException");
    if (c) env->ThrowNew(c, msg);
}

static std::string jstr(JNIEnv * env, jstring s) {
    if (!s) return "";
    const char * p = env->GetStringUTFChars(s, nullptr);
    std::string out(p ? p : "");
    env->ReleaseStringUTFChars(s, p);
    return out;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_wunding_personachat_data_GgufLlm_nativeLoad(JNIEnv * env, jobject, jstring path, jint threads, jstring libDir) {
    std::string p = jstr(env, path);
    std::string dir = jstr(env, libDir);
    if (!dir.empty()) ggml_backend_load_all_from_path(dir.c_str());
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model * model = llama_model_load_from_file(p.c_str(), mp);
    if (!model) { throwJava(env, "GGUF 加载失败：文件损坏、量化不支持或内存不足"); return 0; }
    Engine * e = new Engine();
    e->model = model;
    e->vocab = llama_model_get_vocab(model);
    const char * t = llama_model_chat_template(model, nullptr);
    e->tmpl = t ? t : "";
    e->threads = threads > 0 ? threads : 4;
    LOGI("model loaded, tmpl=%s", e->tmpl.empty() ? "none" : "embedded");
    return (jlong) e;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wunding_personachat_data_GgufLlm_nativeFree(JNIEnv *, jobject, jlong handle) {
    Engine * e = (Engine *) handle;
    if (!e) return;
    if (e->model) llama_model_free(e->model);
    delete e;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wunding_personachat_data_GgufLlm_nativeCancel(JNIEnv *, jobject, jlong handle) {
    Engine * e = (Engine *) handle;
    if (e) e->cancel.store(true);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wunding_personachat_data_GgufLlm_nativeInfo(JNIEnv * env, jobject, jlong handle) {
    Engine * e = (Engine *) handle;
    if (!e) return env->NewStringUTF("未加载");
    char buf[256];
    snprintf(buf, sizeof(buf), "%s | ctx_train=%d", e->tmpl.empty() ? "无内置模板" : "内置对话模板",
             e->model ? llama_model_n_ctx_train(e->model) : 0);
    return env->NewStringUTF(buf);
}

static bool decodeTokens(Engine * e, llama_context * ctx, std::vector<llama_token> & toks, int pos0) {
    int n = (int) toks.size();
    llama_batch batch = llama_batch_init(n, 0, 1);
    for (int i = 0; i < n; i++) {
        batch.token[i] = toks[i];
        batch.pos[i] = pos0 + i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n - 1);
    }
    batch.n_tokens = n;
    int rc = llama_decode(ctx, batch);
    llama_batch_free(batch);
    return rc == 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wunding_personachat_data_GgufLlm_nativeGenerate(
        JNIEnv * env, jobject, jlong handle,
        jstring jSystem, jobjectArray jRoles, jobjectArray jContents,
        jint maxTokens, jfloat temp, jobject callback) {
    Engine * e = (Engine *) handle;
    if (!e || !e->model) { throwJava(env, "GGUF 模型未加载"); return 0; }
    std::lock_guard<std::mutex> lock(e->mu);
    e->cancel.store(false);

    std::vector<std::string> roles, contents;
    jsize n = jRoles ? env->GetArrayLength(jRoles) : 0;
    for (jsize i = 0; i < n; i++) {
        roles.push_back(jstr(env, (jstring) env->GetObjectArrayElement(jRoles, i)));
        contents.push_back(jstr(env, (jstring) env->GetObjectArrayElement(jContents, i)));
    }
    std::vector<llama_chat_message> msgs;
    std::string sys = jstr(env, jSystem);
    if (!sys.empty()) msgs.push_back({"system", sys.c_str()});
    for (jsize i = 0; i < n; i++) msgs.push_back({roles[i].c_str(), contents[i].c_str()});

    int promptLen = 0;
    if (!e->tmpl.empty()) {
        promptLen = llama_chat_apply_template(e->tmpl.c_str(), msgs.data(), msgs.size(), true, nullptr, 0);
    }
    std::string prompt;
    if (promptLen > 0) {
        prompt.resize(promptLen);
        llama_chat_apply_template(e->tmpl.c_str(), msgs.data(), msgs.size(), true, prompt.data(), promptLen);
    } else {
        prompt = sys + "\n\n";
        for (jsize i = 0; i < n; i++) prompt += (roles[i] == "user" ? "User: " : "Assistant: ") + contents[i] + "\n";
        prompt += "Assistant:";
    }

    int train_ctx = (int) llama_model_n_ctx_train(e->model);
    int n_ctx = train_ctx > 4096 ? 4096 : (train_ctx > 0 ? train_ctx : 2048);
    if (n_ctx < 512) n_ctx = 512;
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = n_ctx;
    cp.n_batch = 256;
    cp.n_threads = e->threads;
    cp.n_threads_batch = e->threads;
    llama_context * ctx = llama_init_from_model(e->model, cp);
    if (!ctx) { throwJava(env, "创建上下文失败：内存不足，换更小量化（Q4_K_M / Q3_K_M）"); return 0; }

    std::vector<llama_token> toks(prompt.size() + 32);
    int nt = llama_tokenize(e->vocab, prompt.c_str(), (int) prompt.size(), toks.data(), (int) toks.size(), true, true);
    if (nt < 0) {
        toks.resize(-nt);
        nt = llama_tokenize(e->vocab, prompt.c_str(), (int) prompt.size(), toks.data(), (int) toks.size(), true, true);
    }
    toks.resize(nt > 0 ? nt : 0);
    // 防超长：完整人设模式可能上万 token，保留开头（模板/系统提示）与结尾（最近对话）
    int cap = n_ctx - std::min<int>(maxTokens, n_ctx / 2) - 8;
    if (cap < 256) cap = 256;
    if ((int) toks.size() > cap) {
        int head = std::min(256, cap / 3);
        std::vector<llama_token> cut;
        cut.insert(cut.end(), toks.begin(), toks.begin() + head);
        cut.insert(cut.end(), toks.end() - (cap - head), toks.end());
        toks.swap(cut);
    }

    int pos = 0;
    bool ok = true;
    for (int i = 0; i < (int) toks.size(); i += 256) {
        int remain = std::min(256, (int) toks.size() - i);
        std::vector<llama_token> chunk(toks.begin() + i, toks.begin() + i + remain);
        ok = decodeTokens(e, ctx, chunk, pos);
        pos += remain;
        if (!ok) break;
    }
    if (!ok) {
        llama_free(ctx); throwJava(env, "prompt 解码失败：上下文太小，缩短人设或历史");
        return 0;
    }

    llama_sampler * chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temp > 0 ? temp : 0.8f));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(1234));

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID cbMethod = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    int generated = 0;
    char piece[512];
    for (int i = 0; i < maxTokens; i++) {
        if (e->cancel.load()) break;
        llama_token id = llama_sampler_sample(chain, ctx, -1);
        if (llama_vocab_is_eog(e->vocab, id)) break;
        int np = llama_token_to_piece(e->vocab, id, piece, sizeof(piece), 0, false);
        if (np > 0) {
            std::string s(piece, np);
            jstring js = env->NewStringUTF(s.c_str());
            env->CallVoidMethod(callback, cbMethod, js);
            env->DeleteLocalRef(js);
            if (env->ExceptionCheck()) break;
        }
        std::vector<llama_token> one{id};
        if (!decodeTokens(e, ctx, one, pos)) break;
        pos++;
        generated++;
    }
    llama_sampler_free(chain);
    llama_free(ctx);
    return generated;
}
