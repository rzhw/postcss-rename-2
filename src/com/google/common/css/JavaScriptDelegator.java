package com.google.common.css;

import com.coveo.nashorn_modules.AbstractFolder;
import com.coveo.nashorn_modules.Folder;
import com.coveo.nashorn_modules.Require;
import jdk.nashorn.api.scripting.NashornScriptEngine;

import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.stream.Collectors;

public class JavaScriptDelegator {

  private NashornScriptEngine engine;
  private String mainModule;
  private String mainImportName;

  public JavaScriptDelegator(String mainModule, String mainImportName) {
    this.mainModule = mainModule;
    this.mainImportName = mainImportName;

    System.setProperty("nashorn.args", "--language=es6");
    NashornScriptEngine engine = (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
    try {
      engine.eval("console = { log: print, warn: print, error: print }");
    } catch (ScriptException e) {
      throw new RuntimeException("Couldn't initialize console", e);
    }
    try {
      Require.enable(engine, createRootFolder("com/google/common/css/lol", "UTF-8"));
    } catch (ScriptException e) {
      throw new RuntimeException("Couldn't initialize nashorn-commonjs-modules", e);
    };
    this.engine = engine;
  }

  public void initialize() {
    try {
      engine.eval("const delegatedMap = (() => { const Map = require('./" + mainImportName + "'); return new Map() })()");
    } catch (ScriptException e) {
      throw new RuntimeException("Couldn't initialize " + mainModule, e);
    }
  }

  public void initialize(Object arg1) {
    try {
      engine.put("arg1", arg1);
      engine.eval("const delegatedMap = (() => { const Map = require('./" + mainImportName + "'); return new Map(Java.from(arg1)) })()");
    } catch (ScriptException e) {
      throw new RuntimeException("Couldn't initialize " + mainModule, e);
    }
  }

  public void initialize(Object arg1, Object arg2) {
    try {
      engine.put("arg1", arg1);
      engine.put("arg2", arg2);
      engine.eval("const delegatedMap = (() => { const Map = require('./" + mainImportName + "'); return new Map(Java.from(arg1), Java.from(arg2)) })()");
    } catch (ScriptException e) {
      throw new RuntimeException("Couldn't initialize " + mainModule, e);
    }
  }

  public void initialize(Object arg1, Object arg2, Object arg3) {
    try {
      engine.put("arg1", arg1);
      engine.put("arg2", arg2);
      engine.put("arg3", arg3);
      engine.eval("const delegatedMap = (() => { const Map = require('./" + mainImportName + "'); return new Map(Java.from(arg1), Java.from(arg2), Java.from(arg3)) })()");
    } catch (ScriptException e) {
      throw new RuntimeException("Couldn't initialize " + mainModule, e);
    }
  }

  public DataFolder createRootFolder(String path, String encoding) {
    return new DataFolder(path, null, "/", encoding);
  }

  public String substitutionMapGet(String key) {
    try {
      engine.put("key", key);
      return engine.eval("delegatedMap.get(key)").toString();
    } catch (ScriptException e) {
      if (e.getMessage().contains("value is null")) {
        throw new NullPointerException();
      }
      throw new RuntimeException("Eval failed", e);
    }
  }

  private class DataFolder extends AbstractFolder {

    private ClassLoader loader;
    private String resourcePath;
    private String encoding;

    @Override
    public String getFile(String name) {
      String path = resourcePath + "/" + name;

      // debug is used by conditional, but we don't need it.
      if (path.contains("debug/node.js")) {
        return "module.exports = (module_name) => ((message) => {});";
      }

      InputStream stream = loader.getResourceAsStream(resourcePath + "/" + name);
      if (stream == null) {
        return null;
      }

      String result = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));

      // Node.js requires shouldn't be using the Babel-generated format.
      result = result.replace("_interopRequireDefault(require(\"conditional\"))", "require('conditional')");

      // The top level module needs to export itself in Node.js style, instead of what Babel generates.
      if (path.contains(mainImportName + ".js")) {
        result += "\nmodule.exports = " + mainModule + ";";
      }

      return result;
    }

    @Override
    public Folder getFolder(String name) {
      if (name.contains("node_modules") && getParent() == null) {
        return new DataFolder(
                "external/npm/node_modules/conditional/node_modules/debug/node_modules/ms/node_modules", null, getPath() + name + "/", encoding);
      }
      return new DataFolder(
              resourcePath + "/" + name, this, getPath() + name + "/", encoding);
    }

    DataFolder(String resourcePath, Folder parent, String displayPath, String encoding) {
      super(parent, displayPath);
      this.loader = JavaScriptDelegator.class.getClassLoader();
      this.resourcePath = resourcePath;
      this.encoding = encoding;
    }
  }
}
