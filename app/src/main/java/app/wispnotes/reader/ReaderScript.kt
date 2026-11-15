package app.wispnotes.reader

import app.wispnotes.note.AiLink
import org.json.JSONArray
import org.json.JSONObject

/**
 * The JavaScript half of the reader: it types the prompt into meta.ai, sends it, waits for the stream
 * to end and hands the answer back. Ported from the working desktop extension (`save-post/src/metaai.js`)
 * and kept deliberately close to it: the selectors and the end-of-stream rule are the fragile part, and
 * they are the part that was measured.
 *
 * No `addJavascriptInterface`: the page can then only be read by [state] from Kotlin's side, so nothing
 * on meta.ai can call into the app.
 */
object ReaderScript {
    /**
     * The extension's prompt, word for word. The wording matters: asking for "every article, book, tool
     * and person ... with links" is what made meta.ai return the article links. "List every link" got
     * "no external links are mentioned" out of the same reel.
     */
    const val PROMPT =
        "Summarise this reel in 5 bullets.\n" +
            "List every article, book, tool and person it mentions, with links.\n" +
            "Then give the full transcript."

    /** meta.ai's own page. The reel travels inside the prompt, so there is no URL to load per note. */
    const val HOME = "https://www.meta.ai/"

    /** Installs `window.__wisp`. Idempotent: a reloaded page gets it again. */
    val INSTALL: String = """
        (function () {
          if (window.__wisp && window.__wisp.state) return 'ready';
          var SEL = {
            composer: '[data-testid="composer-input"][contenteditable="true"]',
            send: '[data-testid="composer-send-button"]',
            stop: '[data-testid="composer-stop-button"]',
            answer: '[data-testid="assistant-message"]'
          };
          var run = { stage: 'idle', error: null, answer: '', links: [], chatUrl: null };
          var busy = false;

          function txt(el) { return (el && (el.innerText || el.textContent) || '').trim(); }
          function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
          function answers() { return document.querySelectorAll(SEL.answer); }
          function lastAnswer() { var a = answers(); return a.length ? a[a.length - 1] : null; }

          function loginWall() {
            var body = document.body ? document.body.innerText : '';
            if (/log in to|sign in to continue|continue with facebook/i.test(body)) return true;
            var links = document.querySelectorAll('a[href*="login"]');
            for (var i = 0; i < links.length; i++) {
              var r = links[i].getBoundingClientRect();
              if (r.width > 0 && r.height > 0) return true;
            }
            return false;
          }

          function waitFor(fn, ms) {
            var deadline = Date.now() + ms;
            return new Promise(function (resolve) {
              (function tick() {
                var v = null;
                try { v = fn(); } catch (e) { v = null; }
                if (v) return resolve(v);
                if (Date.now() > deadline) return resolve(null);
                setTimeout(tick, 250);
              })();
            });
          }

          /* Every link arrives wrapped as https://l.meta.ai/?u=<encoded>&h=...; the real address is u. */
          function linksIn(root) {
            var seen = {}, out = [], anchors = root.querySelectorAll('a[href]');
            for (var i = 0; i < anchors.length; i++) {
              var href = anchors[i].href || '';
              try {
                var u = new URL(href);
                if (u.hostname === 'l.meta.ai') href = u.searchParams.get('u') || href;
              } catch (e) { /* keep as is */ }
              if (!/^https?:/i.test(href)) continue;
              if (/instagram\.com\/(reel|p)\//.test(href)) continue;
              if (seen[href]) continue;
              seen[href] = true;
              out.push({ url: href, text: txt(anchors[i]).slice(0, 120) });
            }
            return out;
          }

          /* Three routes, each verified: a WebView can refuse execCommand and still answer "ok". */
          async function fill(composer, value) {
            var head = value.slice(0, 24);
            function landed() { return (composer.innerText || '').indexOf(head) >= 0; }
            try { composer.focus(); document.execCommand('selectAll'); document.execCommand('delete'); } catch (e) {}
            await sleep(100);
            var routes = [
              function () { composer.focus(); document.execCommand('insertText', false, value); },
              function () {
                var dt = new DataTransfer();
                dt.setData('text/plain', value);
                composer.dispatchEvent(new ClipboardEvent('paste', { clipboardData: dt, bubbles: true, cancelable: true }));
              },
              function () {
                composer.dispatchEvent(new InputEvent('beforeinput', { inputType: 'insertText', data: value, bubbles: true, cancelable: true }));
              }
            ];
            for (var i = 0; i < routes.length; i++) {
              try { routes[i](); } catch (e) { continue; }
              await sleep(350);
              if (landed()) return i;
            }
            return -1;
          }

          async function go(url, prompt) {
            if (busy) return false;
            busy = true;
            run.answer = '';
            run.links = [];
            run.chatUrl = null;
            run.error = null;
            function fail(error) {
              run.stage = 'failed';
              run.error = error;
              busy = false;
              return false;
            }
            run.stage = 'composer';
            var composer = await waitFor(function () { return document.querySelector(SEL.composer); }, 60000);
            if (!composer) return fail(loginWall() ? 'needs_login' : 'composer_not_ready');
            run.stage = 'typing';
            if (await fill(composer, url + '\n' + prompt) < 0) return fail('composer_rejected');
            var send = await waitFor(function () {
              var b = document.querySelector(SEL.send);
              return b && !b.disabled ? b : null;
            }, 8000);
            if (!send) return fail('composer_rejected');
            send.click();
            run.stage = 'sent';
            await sleep(700);
            /* a click can land before the button is wired; retry once if the text is still there */
            if ((composer.innerText || '').indexOf(url.slice(0, 24)) >= 0) {
              var again = document.querySelector(SEL.send);
              if (again && !again.disabled) again.click();
            }
            var first = await waitFor(lastAnswer, 60000);
            if (!first) {
              var body = document.body ? document.body.innerText : '';
              return fail(/something went wrong|try again|can't help with that/i.test(body) ? 'meta_error' : 'no_answer');
            }
            run.stage = 'reading';
            /* the send button stays disabled after a send, so it is no use as a signal;
               the stop button disappearing plus a stable length is what works */
            var stable = 0, last = -1, deadline = Date.now() + 240000;
            while (Date.now() < deadline) {
              await sleep(500);
              var el = lastAnswer();
              var len = el ? txt(el).length : 0;
              if (len === last && len > 0) stable++; else stable = 0;
              last = len;
              if (!document.querySelector(SEL.stop) && stable >= 3) break;
            }
            var done = lastAnswer();
            var answer = txt(done);
            if (!answer) return fail('no_answer');
            run.answer = answer;
            run.links = linksIn(done);
            run.chatUrl = location.href.indexOf('https://www.meta.ai/prompt/') === 0 ? location.href : null;
            run.stage = 'done';
            busy = false;
            return true;
          }

          window.__wisp = {
            start: function (url, prompt) { go(url, prompt); return true; },
            state: function () {
              return {
                stage: run.stage,
                error: run.error,
                answer: run.answer,
                links: run.links,
                chatUrl: run.chatUrl,
                composer: !!document.querySelector(SEL.composer),
                /* only worth scanning the page when there is no box; innerText forces layout */
                login: !document.querySelector(SEL.composer) && loginWall(),
                page: location.href
              };
            }
          };
          return 'installed';
        })()
    """.trimIndent()

    fun start(url: String, prompt: String = PROMPT): String =
        "(function(){ if(!window.__wisp) return 'missing'; return window.__wisp.start(${quote(url)}, ${quote(prompt)}); })()"

    fun state(): String = "(function(){ return window.__wisp ? window.__wisp.state() : null; })()"

    /** A JS string literal. The reel URL and the prompt both go through here. */
    fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '<' -> append("\\u003c") // never let a string close the surrounding tag
                '>' -> append("\\u003e")
                '&' -> append("\\u0026")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}

/** What the page says about itself. Built by [ReaderScript.state] and parsed by [parse]. */
data class ReaderState(
    val stage: String,
    val error: String?,
    val answer: String,
    val links: List<AiLink>,
    val chatUrl: String?,
    /** The composer is on screen: the page loaded, and a guest may use it. */
    val composer: Boolean,
    /** The page is showing a sign-in wall instead of a composer. */
    val login: Boolean,
    val page: String,
) {
    val done: Boolean get() = stage == "done" && answer.isNotBlank()
    val failed: Boolean get() = stage == "failed"

    /** One line for the overlay, in the app's voice. */
    fun label(): String = when {
        failed -> errorLabel(error)
        stage == "idle" -> "Opening Meta AI…"
        stage == "composer" -> "Waiting for the box on meta.ai…"
        stage == "typing" -> "Typing the reel in…"
        stage == "sent" -> "Sent. Waiting for the answer…"
        stage == "reading" -> "Reading the reel. About 30 seconds."
        stage == "done" -> "Got it. Saving to the note…"
        else -> "Reading…"
    }

    companion object {
        val idle = ReaderState("idle", null, "", emptyList(), null, false, false, "")

        fun errorLabel(error: String?): String = when (error) {
            "needs_login" -> "Meta AI wants a sign-in. Sign in below, then try again."
            "composer_not_ready" -> "The prompt box never appeared. Check the connection, then try again."
            "composer_rejected" -> "Meta AI would not accept the text. Try again."
            "meta_error" -> "Meta AI answered with an error."
            "no_answer" -> "No answer came back. Try again."
            "timeout" -> "This took more than six minutes. Try again."
            else -> "Something went wrong. Try again."
        }

        /** Null when [raw] is not a state object (the page moved, or the script is gone). */
        fun parse(raw: String?): ReaderState? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty() || text == "null") return null
            return runCatching {
                val o = JSONObject(text)
                val links = mutableListOf<AiLink>()
                val array: JSONArray = o.optJSONArray("links") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val link = array.optJSONObject(i) ?: continue
                    val url = link.optString("url")
                    if (url.isNotBlank()) links.add(AiLink(link.optString("text"), url))
                }
                ReaderState(
                    stage = o.optString("stage", "idle"),
                    error = o.optString("error").takeIf { it.isNotBlank() && it != "null" },
                    answer = o.optString("answer"),
                    links = links,
                    chatUrl = o.optString("chatUrl").takeIf { it.isNotBlank() && it != "null" },
                    composer = o.optBoolean("composer"),
                    login = o.optBoolean("login"),
                    page = o.optString("page"),
                )
            }.getOrNull()
        }
    }
}
