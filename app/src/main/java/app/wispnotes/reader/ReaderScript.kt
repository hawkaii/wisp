package app.wispnotes.reader

import app.wispnotes.note.AiLink
import org.json.JSONArray
import org.json.JSONObject

/**
 * The JavaScript half of the reader: it types the prompt into meta.ai, sends it, waits for the stream
 * to end and hands the answer back. Ported from the working desktop extension (`save-post/src/metaai.js`)
 * and kept close to it, because the selectors and the end-of-stream rule are the measured part.
 *
 * Two things the extension never met, because it ran in a logged-in desktop Chrome:
 *
 * - **Mobile meta.ai is a different page.** It has no `data-testid` composer; the box is
 *   `input[aria-label="Ask Meta AI"]` and the button is `button[aria-label="Send"]`. A phone WebView
 *   only ever sees this one. Both layouts are tried, desktop first.
 * - **A fresh guest is asked for a birth year.** That is a stage, not an error: the page stays
 *   interactive, the bar asks the user to finish it, and [resume] carries on.
 *
 * No `addJavascriptInterface`: the page can only be read from Kotlin's side, so nothing on meta.ai can
 * call into the app.
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
            composerInput: 'input[aria-label="Ask Meta AI"]',
            composerAny: '[contenteditable="true"][role="textbox"]',
            send: '[data-testid="composer-send-button"]',
            sendAny: 'button[aria-label="Send"]',
            stop: '[data-testid="composer-stop-button"]',
            answer: '[data-testid="assistant-message"]'
          };
          var run = { stage: 'idle', error: null, answer: '', links: [], chatUrl: null, gate: false, tap: null };
          var busy = false;

          function txt(el) { return (el && (el.innerText || el.textContent) || '').trim(); }
          function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
          function shown(el) { return !!(el && (el.offsetWidth || el.offsetHeight)); }
          function first(selectors) {
            for (var i = 0; i < selectors.length; i++) {
              var list = document.querySelectorAll(selectors[i]);
              for (var j = 0; j < list.length; j++) if (shown(list[j])) return list[j];
            }
            return null;
          }
          function composer() { return first([SEL.composer, SEL.composerInput, SEL.composerAny]); }
          function sendButton() { return first([SEL.send, SEL.sendAny]); }
          function answers() { return document.querySelectorAll(SEL.answer); }
          function lastAnswer() { var a = answers(); return a.length ? a[a.length - 1] : null; }
          function streaming() { return !!document.querySelector(SEL.stop); }

          function pageText() { return document.body ? document.body.innerText : ''; }

          /* A phone WebView is always a brand-new guest, so this is the first thing it meets. */
          function gateOpen() { return /date of birth/i.test(pageText()); }

          /* meta.ai's own sign-out marker. The only login signal the page gives that is worth trusting. */
          function signedOut() { return !!document.querySelector('[data-testid="login-button"]'); }

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

          /* The mobile box is a real <input>: React only reads a value set through the native setter
             followed by an input event. execCommand is tried as well, for the contenteditable layout. */
          function setValue(el, value) {
            var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
            var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
            setter.call(el, value);
            el.dispatchEvent(new Event('input', { bubbles: true }));
          }

          async function fill(el, value) {
            var head = value.slice(0, 24);
            function boxValue() { return el.tagName === 'DIV' ? (el.innerText || '') : (el.value || ''); }
            function landed() { return boxValue().indexOf(head) >= 0; }
            el.focus();
            if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
              setValue(el, value);
              await sleep(300);
              if (landed()) return true;
            }
            try { document.execCommand('selectAll'); document.execCommand('delete'); } catch (e) {}
            await sleep(80);
            var routes = [
              function () { el.focus(); document.execCommand('insertText', false, value); },
              function () {
                var dt = new DataTransfer();
                dt.setData('text/plain', value);
                el.dispatchEvent(new ClipboardEvent('paste', { clipboardData: dt, bubbles: true, cancelable: true }));
              },
              function () {
                el.dispatchEvent(new InputEvent('beforeinput', { inputType: 'insertText', data: value, bubbles: true, cancelable: true }));
              }
            ];
            for (var i = 0; i < routes.length; i++) {
              try { routes[i](); } catch (e) { continue; }
              await sleep(300);
              if (landed()) return true;
            }
            return false;
          }

          /* The mobile Send button ignores a semantic click: the page wants a real gesture
             (navigator.userActivation). Kotlin dispatches a real touch at this rectangle instead. */
          function rectOf(el) {
            var r = el.getBoundingClientRect();
            return { x: r.x + r.width / 2, y: r.y + r.height / 2, dpr: window.devicePixelRatio || 1 };
          }

          async function harvest(el) {
            var stable = 0, last = -1, deadline = Date.now() + 240000;
            run.stage = 'reading';
            while (Date.now() < deadline) {
              await sleep(500);
              var current = lastAnswer();
              var len = current ? txt(current).length : 0;
              if (len === last && len > 0) stable++; else stable = 0;
              last = len;
              /* the send button stays disabled after a send, so the stop button plus a stable
                 length is what marks the end of the stream */
              if (!streaming() && stable >= 3) break;
            }
            var done = lastAnswer() || el;
            var answer = txt(done);
            busy = false;
            if (!answer) {
              run.stage = 'failed';
              run.error = 'no_answer';
              return false;
            }
            run.answer = answer;
            run.links = linksIn(done);
            run.chatUrl = location.href.indexOf('https://www.meta.ai/prompt/') === 0 ? location.href : null;
            run.gate = false;
            run.stage = 'done';
            return true;
          }

          async function go(url, prompt) {
            if (busy) return false;
            busy = true;
            run.answer = '';
            run.links = [];
            run.chatUrl = null;
            run.error = null;
            run.gate = false;
            run.tap = null;
            function fail(error) {
              run.stage = 'failed';
              run.error = error;
              busy = false;
              return false;
            }
            /* Not a failure: the page is usable, the user just has to answer it. busy goes back to
               false so the Continue button can take over. */
            function waitForUser() {
              run.stage = 'needs_setup';
              run.gate = true;
              busy = false;
              return false;
            }
            run.stage = 'composer';
            var box = null, lookUntil = Date.now() + 45000;
            while (Date.now() < lookUntil) {
              box = composer();
              if (box) break;
              if (gateOpen()) return waitForUser();
              if (signedOut()) return fail('needs_login');
              await sleep(500);
            }
            if (!box) return fail('composer_not_ready');
            run.stage = 'typing';
            if (!(await fill(box, url + '\n' + prompt))) return gateOpen() ? waitForUser() : fail('composer_rejected');
            var send = await waitFor(sendButton, 8000);
            if (!send) return fail('composer_rejected');
            /* Hand the rectangle to Kotlin: only a real touch from the view counts as a gesture,
               and the page ignores anything less for this button. */
            run.stage = 'sending';
            run.tap = rectOf(send);
            await waitFor(function () { return !run.tap; }, 10000);
            run.stage = 'sent';
            await sleep(1200);
            if (gateOpen()) return waitForUser();
            run.stage = 'reading';
            var el = null, deadline = Date.now() + 120000;
            while (Date.now() < deadline) {
              el = lastAnswer();
              if (el) break;
              if (gateOpen()) return waitForUser();
              await sleep(500);
            }
            if (!el) {
              return fail(/something went wrong|try again|can't help with that/i.test(pageText()) ? 'meta_error' : 'no_answer');
            }
            return harvest(el);
          }

          /* After the user passes the gate: take the answer if the page carried on by itself,
             otherwise send again. */
          async function resume(url, prompt) {
            if (busy) return false;
            var existing = lastAnswer();
            if (existing && run.stage !== 'done') {
              busy = true;
              return harvest(existing);
            }
            return go(url, prompt);
          }

          window.__wisp = {
            start: function (url, prompt) { go(url, prompt); return true; },
            resume: function (url, prompt) { resume(url, prompt); return true; },
            tapped: function () { run.tap = null; return true; },
            state: function () {
              var box = composer();
              return {
                stage: run.stage,
                error: run.error,
                answer: run.answer,
                links: run.links,
                chatUrl: run.chatUrl,
                composer: !!box,
                gate: run.gate || (!box && gateOpen()),
                login: signedOut(),
                answers: answers().length,
                tap: run.tap,
                page: location.href
              };
            }
          };
          return 'installed';
        })()
    """.trimIndent()

    fun start(url: String, prompt: String = PROMPT): String =
        "(function(){ if(!window.__wisp) return 'missing'; return window.__wisp.start(${quote(url)}, ${quote(prompt)}); })()"

    /** Called by the Continue button once the user has dealt with the page itself. */
    fun resume(url: String, prompt: String = PROMPT): String =
        "(function(){ if(!window.__wisp) return 'missing'; return window.__wisp.resume(${quote(url)}, ${quote(prompt)}); })()"

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

/** Where the WebView must be tapped, in CSS pixels, scaled by [dpr]. */
data class TapPoint(val x: Float, val y: Float, val dpr: Float)

/** What the page says about itself. Built by [ReaderScript.state] and parsed by [parse]. */
data class ReaderState(
    val stage: String,
    val error: String?,
    val answer: String,
    val links: List<AiLink>,
    val chatUrl: String?,
    /** A prompt box is on screen. */
    val composer: Boolean,
    /** The birth-year gate for a fresh guest is on screen: the user finishes it, not the app. */
    val gate: Boolean,
    /** The page is showing a sign-in wall instead of a prompt box. */
    val login: Boolean,
    /** How many answers are on the page. A gate can be followed by an answer without our help. */
    val answers: Int,
    /** Set while the page waits for a real tap on its Send button. */
    val tap: TapPoint?,
    val page: String,
) {
    val done: Boolean get() = stage == "done" && answer.isNotBlank()
    val failed: Boolean get() = stage == "failed"

    /** The app stopped and the user has something to do on the page. */
    val waitingForUser: Boolean get() = stage == "needs_setup"

    /** One line for the overlay, in the app's voice. */
    fun label(): String = when {
        failed -> errorLabel(error)
        waitingForUser -> "Meta AI asks your birth year once. Set it on the page above, then tap Continue."
        stage == "idle" -> "Opening Meta AI…"
        stage == "composer" -> "Waiting for the box on meta.ai…"
        stage == "typing" -> "Typing the reel in…"
        stage == "sending" -> "Sending to Meta AI…"
        stage == "sent" -> "Sent. Waiting for the answer…"
        stage == "reading" -> "Reading the reel. About 30 seconds."
        stage == "done" -> "Got it. Saving to the note…"
        else -> "Reading…"
    }

    companion object {
        val idle = ReaderState("idle", null, "", emptyList(), null, false, false, false, 0, null, "")

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
                val tap = o.optJSONObject("tap")?.let {
                    TapPoint(it.optDouble("x", 0.0).toFloat(), it.optDouble("y", 0.0).toFloat(), it.optDouble("dpr", 1.0).toFloat())
                }
                ReaderState(
                    stage = o.optString("stage", "idle"),
                    error = o.optString("error").takeIf { it.isNotBlank() && it != "null" },
                    answer = o.optString("answer"),
                    links = links,
                    chatUrl = o.optString("chatUrl").takeIf { it.isNotBlank() && it != "null" },
                    composer = o.optBoolean("composer"),
                    gate = o.optBoolean("gate"),
                    login = o.optBoolean("login"),
                    answers = o.optInt("answers"),
                    tap = tap,
                    page = o.optString("page"),
                )
            }.getOrNull()
        }
    }
}
