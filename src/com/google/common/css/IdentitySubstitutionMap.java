/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.css;

import com.coveo.nashorn_modules.AbstractFolder;
import com.coveo.nashorn_modules.Folder;
import com.coveo.nashorn_modules.Require;
import com.coveo.nashorn_modules.ResourceFolder;
import com.google.common.base.Preconditions;
import jdk.nashorn.api.scripting.NashornScriptEngine;

import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * {@link IdentitySubstitutionMap} is a trivial implementation of
 * {@link SubstitutionMap} that returns the key as the value for the requested
 * key.
 *
 * @author bolinfest@google.com (Michael Bolin)
 */
public class IdentitySubstitutionMap implements SubstitutionMap {

  public static class DataFolder extends AbstractFolder {
    /*ResourceFolder rootDelegate;
    ResourceFolder nodeModulesDelegate;

    public DataFolder() {
      rootDelegate = ResourceFolder.create(getClass().getClassLoader(), "com/google/common/css/lol", "UTF-8");
      nodeModulesDelegate = ResourceFolder.create(getClass().getClassLoader(), "external/npm/node_modules", "UTF-8");
    }

    public String getFile(String name) {
      if (name.contains("node_modules")) {
        throw new RuntimeException("boo " + name);
      }
      return rootDelegate.getFile(name);
    }

    public Folder getFolder(String name) {
      if (name.endsWith("node_modules")) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("external/npm/node_modules/conditional/node_modules/debug/node_modules/ms/node_modules/conditional/lib/util.js");
        if (stream == null) {
          return null;
        }

        //try {
        //  throw new RuntimeException("hello"+new BufferedReader(new InputStreamReader(stream))
        //          .lines().collect(Collectors.joining("\n")));
        //} catch (IOException ex) {
        //  return null;
        //}
        return nodeModulesDelegate.getFolder(name);
      }
      return rootDelegate.getFolder(name);
    }

    public Folder getParent() {
      return null;
    }

    public String getPath() {
      return "/";
    }*/


    private ClassLoader loader;
    private String resourcePath;
    private String encoding;

    @Override
    public String getFile(String name) {
      String path = resourcePath + "/" + name;
      System.out.println("getFile " + path);
      if (path.contains("debug/node.js")) {
        System.out.println("debug shim");
        return "module.exports = (module_name) => ((message) => {});";
      }
      InputStream stream = loader.getResourceAsStream(resourcePath + "/" + name);
      if (stream == null) {
        System.out.println("not found!");
        return null;
      }

      String result = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
      if (path.contains("identity-substitution-map.js")) {
        result = result.replace("_interopRequireDefault(require(\"conditional\"))", "require('conditional')");
        result += "\nmodule.exports = IdentitySubstitutionMap;";
      }
      return result;
    }

    @Override
    public Folder getFolder(String name) {
      System.out.println("getFolder " + (resourcePath + "/" + name));
      if (name.contains("node_modules") && getParent() == null) {
        return new DataFolder(
                loader, "external/npm/node_modules/conditional/node_modules/debug/node_modules/ms/node_modules", null, getPath() + name + "/", encoding);
      }
      return new DataFolder(
              loader, resourcePath + "/" + name, this, getPath() + name + "/", encoding);
    }

    private DataFolder(
            ClassLoader loader, String resourcePath, Folder parent, String displayPath, String encoding) {
      super(parent, displayPath);
      this.loader = loader;
      this.resourcePath = resourcePath;
      this.encoding = encoding;
    }
  }

  public static DataFolder create(ClassLoader loader, String path, String encoding) {
    return new DataFolder(loader, path, null, "/", encoding);
  }

  NashornScriptEngine engine;

  public IdentitySubstitutionMap() {
    System.setProperty("nashorn.args", "--language=es6");
    engine = (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
    try {
      Require.enable(engine, create(getClass().getClassLoader(), "com/google/common/css/lol", "UTF-8"));
    } catch (ScriptException e) {
      throw new RuntimeException("Couldn't initialize nashorn-commonjs-modules", e);
    }
  }

  @Override
  public String get(String key) {
    try {
      engine.put("key", key);
      return engine.eval("(() => { const Map = require('./identity-substitution-map'); return new Map().get(key) })()").toString();
    } catch (ScriptException e) {
      if (e.getMessage().contains("value is null")) {
        throw new NullPointerException();
      }
      throw new RuntimeException("Eval failed", e);
    }
  }
}
