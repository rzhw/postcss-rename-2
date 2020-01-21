package com.google.common.css;

import com.coveo.nashorn_modules.AbstractFolder;
import com.coveo.nashorn_modules.Folder;
import com.coveo.nashorn_modules.Require;
import com.google.common.css.MultipleMappingSubstitutionMap.ValueWithMappings;
import jdk.nashorn.api.scripting.NashornScriptEngine;

import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class JavaScriptDelegator {

  public interface Delegating {
    public Object getDelegatedJSObject();
  }

  Map<String, String> myMap = new HashMap<String, String>() {{
    put("identity-substitution-map.js", "IdentitySubstitutionMap");
    put("minimal-substitution-map.js", "MinimalSubstitutionMap");
    put("prefixing-substitution-map.js", "PrefixingSubstitutionMap");
    put("simple-substitution-map.js", "SimpleSubstitutionMap");
    put("splitting-substitution-map.js", "SplittingSubstitutionMap");
  }};

  private static NashornScriptEngine engine;
  private String mainModule;
  private String mainImportName;
  public Object delegatedMap;

  public JavaScriptDelegator(String mainModule, String mainImportName) {
    this.mainModule = mainModule;
    this.mainImportName = mainImportName;

    if (engine == null) {
      System.setProperty("nashorn.args", "--language=es6");
      engine = (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
      try {
        // console doesn't exist.
        engine.eval("console = { log: print, warn: print, error: print }");

        // Number.isInteger doesn't exist.
        engine.eval("" +
                "Number.isInteger = Number.isInteger || function(value) {\n" +
                "  return typeof value === 'number' && \n" +
                "    isFinite(value) && \n" +
                "    Math.floor(value) === value;\n" +
                "}");
      } catch (ScriptException e) {
        throw new RuntimeException("Couldn't initialize polyfills", e);
      }
      try {
        Require.enable(engine, createRootFolder("com/google/common/css/lol", "UTF-8"));
      } catch (ScriptException e) {
        throw new RuntimeException("Couldn't initialize nashorn-commonjs-modules", e);
      }
    }
  }

  public void initialize() {
    try {
      delegatedMap = engine.eval("(() => { const Map = require('./" + mainImportName + "'); return new Map() })()");
    } catch (ScriptException e) {
      throw new RuntimeException("Couldn't initialize " + mainModule, e);
    }
  }

  public void initialize(Object arg1) {
    try {
      engine.put("arg1", arg1);
      String arg1Import = "arg1";
      if (arg1 instanceof List) {
        arg1Import = "Java.from(arg1)";
      }
      delegatedMap = engine.eval("(() => { const TheMap = require('./" + mainImportName + "'); return new TheMap("+arg1Import+") })()");
    } catch (ScriptException e) {
      throw new RuntimeException("Couldn't initialize " + mainModule, e);
    }
  }

  public void initialize(Object arg1, Object arg2) {
    try {
      engine.put("arg1", arg1);
      engine.put("arg2", arg2);
      String arg1Import = "arg1";
      if (arg1 instanceof List) {
        arg1Import = "Java.from(arg1)";
      }
      String arg2Import = "arg2";
      if (arg2 instanceof List) {
        arg2Import = "Java.from(arg2)";
      }
      delegatedMap = engine.eval("(() => { const Map = require('./" + mainImportName + "'); return new Map("+arg1Import+", "+arg2Import+") })()");
    } catch (ScriptException e) {
      throw new RuntimeException("Couldn't initialize " + mainModule, e);
    }
  }

  public void initialize(Object arg1, Object arg2, Object arg3) {
    try {
      engine.put("arg1", arg1);
      engine.put("arg2", arg2);
      engine.put("arg3", arg3);
      delegatedMap = engine.eval("(() => { const Map = require('./" + mainImportName + "'); return new Map(Java.from(arg1), Java.from(arg2), Java.from(arg3)) })()");
    } catch (ScriptException e) {
      throw new RuntimeException("Couldn't initialize " + mainModule, e);
    }
  }

  public DataFolder createRootFolder(String path, String encoding) {
    return new DataFolder(path, null, "/", encoding);
  }

  public String substitutionMapGet(String key) {
    return executeObject("get", key).toString();
  }

  public void substitutionMapInitializableInitializeWithMappings(Map<? extends String, ? extends String> initialMappings) {
    try {
      engine.put("delegatedMap", delegatedMap);
      // immutable.Map expects a JavaScript Object, so we need to pre-convert.
      engine.put("initialMappings", initialMappings);
      engine.eval("(() => {" +
        "const immutable = require('immutable');" +
        "const map = {}; for each (let i in initialMappings.keySet()) { map[i] = initialMappings.get(i) };" +
        "delegatedMap.initializeWithMappings(immutable.Map(map)) })()");
    } catch (ScriptException e) {
      if (e.getMessage().contains("value is null")) {
        throw new NullPointerException();
      }
      throw new RuntimeException("Eval failed", e);
    }
  }

  public ValueWithMappings multipleMappingSubstitutionMapGetValueWithMappings(String key) {
    try {
      engine.put("delegatedMap", delegatedMap);
      engine.put("key", key);
      return (ValueWithMappings) engine.eval("(() => {" +
        "const HashMap = Java.type('java.util.HashMap');" +
        "const JavaValueWithMappings = Java.type('com.google.common.css.MultipleMappingSubstitutionMap.ValueWithMappings');" +
        "const jsValueWithMappings = delegatedMap.getValueWithMappings(key);" +
        "const map = new HashMap();" +
        "jsValueWithMappings.mappings.forEach((value, key) => map.put(key, value));" +
        "return new JavaValueWithMappings(jsValueWithMappings.value, map) })()");
    } catch (ScriptException e) {
      if (e.getMessage().contains("value is null")) {
        throw new NullPointerException();
      }
      throw new RuntimeException("Eval failed", e);
    }
  }

  public Object executeObject(String method, Object ...args) {
    try {
      engine.put("delegatedMap", delegatedMap);
      engine.put("args", args);
      return engine.eval("delegatedMap." + method + ".apply(delegatedMap, args)");
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

    private String getResource(String path) {
      InputStream stream = loader.getResourceAsStream(path);
      if (stream == null) {
        return null;
      }
      return new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
    }

    @Override
    public String getFile(String name) {
      String path = resourcePath + "/" + name;

      // debug is used by conditional, but we don't need it.
      if (path.contains("debug/node.js")) {
        return "module.exports = (module_name) => ((message) => {});";
      }

      InputStream stream = loader.getResourceAsStream(resourcePath + "/" + name);
      if (stream == null) {
      String result = getResource(path);
      if (result == null) {
        //System.out.println("couldn't find " + path);
        return null;
      }

      // Node.js requires shouldn't be using the Babel-generated format.
      result = result.replace("_interopRequireDefault(require(\"conditional\"))", "require('conditional')");
      result = result.replace("_interopRequireDefault(require(\"immutable\"))", "require('immutable')");

      // The top level module needs to export itself in Node.js style, instead of what Babel generates.
      Optional<String> anyMatch = myMap.keySet().stream().filter(x -> path.endsWith("/" + x)).findFirst();
      if (anyMatch.isPresent()) {
        result += "\nmodule.exports = " + myMap.get(anyMatch.get()) + ";";
      }

      return result;
    }

    @Override
    public Folder getFolder(String name) {
      String path = resourcePath + "/" + name;
      //System.out.println("Looking for " + path);

      if (path.startsWith("com/google/common/css/lol/node_modules/conditional")) {
        return new DataFolder(
                "external/npm/node_modules/conditional/node_modules/debug/node_modules/ms/node_modules/conditional", this, getPath() + name + "/", encoding);
      }
      if (path.startsWith("com/google/common/css/lol/node_modules/immutable")) {
        return new DataFolder(
                "external/npm/node_modules/immutable", this, getPath() + name + "/", encoding);
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
