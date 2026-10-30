package dev.alastorkaneki.chromescript;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Execute the actual generated payload in Node, rather than asserting substrings. */
public class ScriptInjectorTest {
    private static final String URL = "https://example.com/";
    private Userscript script(String source) {
        Userscript s = UserscriptMetadataParser.parse(
                "// ==UserScript==\n// @name Test\n// @match https://example.com/*\n"
                + "// ==/UserScript==\n" + source, "test.user.js", "");
        return s;
    }

    private String evaluate(Userscript s, boolean force) {
        String encoded = Base64.getEncoder().encodeToString(
                ScriptInjector.buildPayload(s, URL, force).getBytes(StandardCharsets.UTF_8));
        return "vm.runInContext(Buffer.from('" + encoded + "','base64').toString(),ctx);\n";
    }

    private void node(String body) throws Exception {
        String harness = "const vm=require('node:vm'),assert=require('node:assert/strict');\n"
                + "const events={},timers=[];let observer;const ctx={count:0,console:{log(){},error(e){throw e}},"
                + "location:{href:'https://example.com/'},document:{readyState:'complete',body:{},documentElement:{}},"
                + "setTimeout:f=>timers.push(f),addEventListener:(n,f)=>events[n]=f,"
                + "MutationObserver:class{constructor(f){observer=this;this.f=f}observe(t){assert.ok(t)}disconnect(){} }};"
                + "ctx.window=ctx;ctx.self=ctx;ctx.top=ctx;vm.createContext(ctx);\n" + body;
        Process p = new ProcessBuilder("node", "-").redirectErrorStream(true).start();
        try (var input = p.getOutputStream()) { input.write(harness.getBytes(StandardCharsets.UTF_8)); }
        assertTrue("Node timed out", p.waitFor(15, TimeUnit.SECONDS));
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(output, 0, p.exitValue());
    }

    @Test public void trailingCommentAndQuotedName() throws Exception {
        Userscript s = script("window.count++; GM_log('ok'); // no final newline");
        s.name = "Alice's \\\"script\\\"";
        node(evaluate(s, false) + "assert.equal(ctx.count,1);");
    }

    @Test public void dependenciesShareLexicalScope() throws Exception {
        Userscript s = script("window.count=dependency(); // end");
        s.requireSources.add("const answer=42; // library comment");
        s.requireSources.add("function dependency(){return answer} // end");
        node(evaluate(s, false) + "assert.equal(ctx.count,42);");
    }

    @Test public void retriesRunOnceButForceRunsAgain() throws Exception {
        Userscript s = script("window.count++;");
        node(evaluate(s, false) + evaluate(s, false) + "assert.equal(ctx.count,1);"
                + evaluate(s, true) + "assert.equal(ctx.count,2);");
    }

    @Test public void wrongDocumentDoesNotConsumeGuard() throws Exception {
        Userscript s = script("window.count++;");
        node("ctx.location.href='https://other.example/';" + evaluate(s, false)
                + "assert.equal(ctx.count,0);ctx.location.href='https://example.com/';"
                + evaluate(s, false) + "assert.equal(ctx.count,1);");
    }

    @Test public void endWaitsForDom() throws Exception {
        Userscript s = script("window.count++;");
        node("ctx.document.readyState='loading';" + evaluate(s, false) + evaluate(s, false)
                + "assert.equal(ctx.count,0);events.DOMContentLoaded();assert.equal(ctx.count,1);");
    }

    @Test public void idleDoesNotWaitForLoad() throws Exception {
        Userscript s = script("window.count++;"); s.runAt = "document-idle";
        node("ctx.document.readyState='interactive';" + evaluate(s, false)
                + "assert.equal(ctx.count,0);timers.shift()();assert.equal(ctx.count,1);");
        node("ctx.document.readyState='loading';" + evaluate(s, false)
                + "assert.equal(timers.length,0);events.DOMContentLoaded();timers.shift()();assert.equal(ctx.count,1);");
    }

    @Test public void bodyCanWaitWithoutRootElement() throws Exception {
        Userscript s = script("window.count++;"); s.runAt = "document-body";
        node("ctx.document.body=null;ctx.document.documentElement=null;" + evaluate(s, false)
                + "assert.equal(ctx.count,0);ctx.document.body={};observer.f([],observer);assert.equal(ctx.count,1);");
    }

    @Test public void startRunsWhileLoading() throws Exception {
        Userscript s = script("window.count++;"); s.runAt = "document-start";
        node("ctx.document.readyState='loading';" + evaluate(s, false) + "assert.equal(ctx.count,1);");
    }

    @Test public void newDocumentAtSameUrlRunsAgain() throws Exception {
        Userscript s = script("window.count++;");
        node(evaluate(s, false) + "assert.equal(ctx.count,1);"
                + "ctx.document={readyState:'complete',body:{}};delete ctx.__MonkeyScriptLoaded;"
                + evaluate(s, false) + evaluate(s, false) + "assert.equal(ctx.count,2);");
    }

    @Test public void noFramesSkipsChildDocument() throws Exception {
        Userscript s = script("window.count++;"); s.noFrames = true;
        node("ctx.top={};" + evaluate(s, false) + "assert.equal(ctx.count,0);");
    }

    @Test public void matchingHonorsDisabledAndExclusions() {
        Userscript s = script("window.count++;");
        assertTrue(s.enabled);
        assertTrue(UrlPatternMatcher.matches(s, URL));
        assertFalse(UrlPatternMatcher.matches(s, "https://other.example/"));
        s.excludes.add("https://example.com/private*");
        assertFalse(UrlPatternMatcher.matches(s, URL + "private"));
        s.enabled = false;
        assertFalse(UrlPatternMatcher.matches(s, URL));
    }
}
