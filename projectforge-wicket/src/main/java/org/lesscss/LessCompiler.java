/* Copyright 2011-2012 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lesscss;

// If less.js 1.3.3 is supported again, remove this package and re-activate the pom.xml entry.

import org.apache.commons.io.FileUtils;
import org.mozilla.javascript.*;
import org.mozilla.javascript.tools.shell.Global;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The LESS compiler to compile LESS sources to CSS stylesheets.
 * <p>
 * The compiler uses Rhino (JavaScript implementation written in Java), Envjs (simulated browser environment written in
 * JavaScript), and the official LESS JavaScript compiler.<br />
 * Note that the compiler is not a Java implementation of LESS itself, but rather integrates the LESS JavaScript
 * compiler within a Java/JavaScript browser environment provided by Rhino and Envjs.
 * </p>
 * <p>
 * The compiler comes bundled with the Envjs and LESS JavaScript, so there is no need to include them yourself. But if
 * needed they can be overridden.
 * </p>
 * <h4>Basic code example:</h4>
 *
 * <pre>
 * LessCompiler lessCompiler = new LessCompiler();
 * String css = lessCompiler.compile("@color: #4D926F; #header { color: @color; }");
 * </pre>
 *
 * @author Marcel Overdijk
 * @see <a href="http://lesscss.org/">LESS - The Dynamic Stylesheet language</a>
 * @see <a href="http://www.mozilla.org/rhino/">Rhino - JavaScript for Java</a>
 * @see <a href="http://www.envjs.com/">Envjs - Bringing the Browser</a>
 */
public class LessCompiler
{

  private static final String COMPILE_STRING = "function doIt(input, compress) { var result; var parser = new less.Parser(); parser.parse(input, function(e, tree) { if (e instanceof Object) { throw e; } ; result = tree.toCSS({compress: compress}); }); return result; }";

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LessCompiler.class);

  private URL envJs = LessCompiler.class.getClassLoader().getResource("lesscss/env.rhino.js");
  private URL lessJs = LessCompiler.class.getClassLoader().getResource("lesscss/less-1.3.3.min.js");
  private List<URL> customJs = Collections.emptyList();
  private boolean compress = false;
  private String encoding = null;

  private Function doIt;

  private Scriptable scope;

  /**
   * Constructs a new <code>LessCompiler</code>.
   */
  public LessCompiler()
  {
  }

  /**
   * Returns the Envjs JavaScript file used by the compiler.
   *
   * @return The Envjs JavaScript file used by the compiler.
   */
  public URL getEnvJs()
  {
    return envJs;
  }

  /**
   * Sets the Envjs JavaScript file used by the compiler. Must be set before {@link #init()} is called.
   *
   * @param envJs The Envjs JavaScript file used by the compiler.
   */
  public synchronized void setEnvJs(final URL envJs)
  {
    if (scope != null) {
      throw new IllegalStateException("This method can only be called before init()");
    }
    this.envJs = envJs;
  }

  /**
   * Returns the LESS JavaScript file used by the compiler. COMPILE_STRING
   *
   * @return The LESS JavaScript file used by the compiler.
   */
  public URL getLessJs()
  {
    return lessJs;
  }

  /**
   * Sets the LESS JavaScript file used by the compiler. Must be set before {@link #init()} is called.
   *
   * @param The LESS JavaScript file used by the compiler.
   */
  public synchronized void setLessJs(final URL lessJs)
  {
    if (scope != null) {
      throw new IllegalStateException("This method can only be called before init()");
    }
    this.lessJs = lessJs;
  }

  /**
   * Returns the custom JavaScript files used by the compiler.
   *
   * @return The custom JavaScript files used by the compiler.
   */
  public List<URL> getCustomJs()
  {
    return Collections.unmodifiableList(customJs);
  }

  /**
   * Sets a single custom JavaScript file used by the compiler. Must be set before {@link #init()} is called.
   *
   * @param customJs A single custom JavaScript file used by the compiler.
   */
  public synchronized void setCustomJs(final URL customJs)
  {
    if (scope != null) {
      throw new IllegalStateException("This method can only be called before init()");
    }
    this.customJs = Collections.singletonList(customJs);
  }

  /**
   * Sets the custom JavaScript files used by the compiler. Must be set before {@link #init()} is called.
   *
   * @param customJs The custom JavaScript files used by the compiler.
   */
  public synchronized void setCustomJs(final List<URL> customJs)
  {
    if (scope != null) {
      throw new IllegalStateException("This method can only be called before init()");
    }
    // copy the list so there's no way for anyone else who holds a reference to the list to modify it
    this.customJs = new ArrayList<URL>(customJs);
  }

  /**
   * Returns whether the compiler will compress the CSS.
   *
   * @return Whether the compiler will compress the CSS.
   */
  public boolean isCompress()
  {
    return compress;
  }

  /**
   * Sets the compiler to compress the CSS. Must be set before {@link #init()} is called.
   *
   * @param compress If <code>true</code>, sets the compiler to compress the CSS.
   */
  public synchronized void setCompress(final boolean compress)
  {
    if (scope != null) {
      throw new IllegalStateException("This method can only be called before init()");
    }
    this.compress = compress;
  }

  /**
   * Returns the character encoding used by the compiler when writing the output <code>File</code>.
   *
   * @return The character encoding used by the compiler when writing the output <code>File</code>.
   */
  public String getEncoding()
  {
    return encoding;
  }

  /**
   * Sets the character encoding used by the compiler when writing the output <code>File</code>. If not set the platform
   * default will be used. Must be set before {@link #init()} is called.
   *
   * @param The character encoding used by the compiler when writing the output <code>File</code>.
   */
  public synchronized void setEncoding(final String encoding)
  {
    if (scope != null) {
      throw new IllegalStateException("This method can only be called before init()");
    }
    this.encoding = encoding;
  }

  /**
   * Initializes this <code>LessCompiler</code>.
   * <p>
   * It is not needed to call this method manually, as it is called implicitly by the compile methods if needed.
   * </p>
   */
  public synchronized void init()
  {
    final long start = System.currentTimeMillis();

    try {
      final Context cx = Context.enter();
      cx.setOptimizationLevel(-1);
      cx.setLanguageVersion(Context.VERSION_1_7);

      final Global global = new Global();
      global.init(cx);

      scope = cx.initStandardObjects(global);

      final List<URL> jsUrls = new ArrayList<URL>(2 + customJs.size());
      jsUrls.add(envJs);
      jsUrls.add(lessJs);
      jsUrls.addAll(customJs);

      for (final URL url : jsUrls) {
        final InputStreamReader inputStreamReader = new InputStreamReader(url.openConnection().getInputStream());
        try {
          cx.evaluateReader(scope, inputStreamReader, url.toString(), 1, null);
        } finally {
          inputStreamReader.close();
        }
      }
      doIt = cx.compileFunction(scope, COMPILE_STRING, "doIt.js", 1, null);
    } catch (final Exception e) {
      final String message = "Failed to initialize LESS compiler.";
      log.error(message, e);
      throw new IllegalStateException(message, e);
    } finally {
      Context.exit();
    }

    if (log.isDebugEnabled()) {
      log.debug("Finished initialization of LESS compiler in " + (System.currentTimeMillis() - start) + " ms.");
    }
  }

  /**
   * Compiles the LESS input <code>String</code> to CSS.
   *
   * @param input The LESS input <code>String</code> to compile.
   * @return The CSS.
   */
  public String compile(final String input) throws LessException
  {
    synchronized (this) {
      if (scope == null) {
        init();
      }
    }

    final long start = System.currentTimeMillis();

    try {
      final Context cx = Context.enter();
      final Object result = doIt.call(cx, scope, null, new Object[] { input, compress });

      if (log.isDebugEnabled()) {
        log.debug("Finished compilation of LESS source in " + (System.currentTimeMillis() - start) + " ms.");
      }

      return result.toString();
    } catch (final Exception e) {
      if (e instanceof JavaScriptException) {
        final Scriptable value = (Scriptable) ((JavaScriptException) e).getValue();
        if (value != null && ScriptableObject.hasProperty(value, "message")) {
          final String message = ScriptableObject.getProperty(value, "message").toString();
          throw new LessException(message, e);
        }
      }
      throw new LessException(e);
    } finally {
      Context.exit();
    }
  }

  /**
   * Compiles the input <code>LessSource</code> to CSS.
   *
   * @param input The input <code>LessSource</code> to compile.
   * @return The CSS.
   */
  public String compile(final LessSource input) throws LessException
  {
    return compile(input.getNormalizedContent());
  }

  /**
   * Compiles the input <code>LessSource</code> to CSS and writes it to the specified output <code>File</code>.
   *
   * @param input The input <code>LessSource</code> to compile.
   * @param output The output <code>File</code> to write the CSS to.
   * @throws IOException If the LESS file cannot be read or the output file cannot be written.
   */
  public void compile(final LessSource input, final File output) throws IOException, LessException
  {
    compile(input, output, true);
  }

  /**
   * Compiles the input <code>LessSource</code> to CSS and writes it to the specified output <code>File</code>.
   *
   * @param input The input <code>LessSource</code> to compile.
   * @param output The output <code>File</code> to write the CSS to.
   * @param force 'false' to only compile the input <code>LessSource</code> in case the LESS source has been modified
   *          (including imports) or the output file does not exists.
   * @throws IOException If the LESS file cannot be read or the output file cannot be written.
   */
  public void compile(final LessSource input, final File output, final boolean force) throws IOException, LessException
  {
    if (force || !output.exists() || output.lastModified() < input.getLastModifiedIncludingImports()) {
      final String data = compile(input);
      FileUtils.writeStringToFile(output, data, encoding);
    }
  }
}
