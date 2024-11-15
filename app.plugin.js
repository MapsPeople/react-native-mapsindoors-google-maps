const {
    withProjectBuildGradle,
    withStringsXml,
    withAndroidManifest,
    withAppDelegate,
    withPlugins,
    withDangerousMod,
    AndroidConfig,
  } = require("@expo/config-plugins");
  
  const path = require("path");
  const fs = require("fs");
  
  /**
   * Plugin to add mapsindoors maven repository
   * @param {*} config expo project config
   * @returns modified config
   */
  const withMavenRepo = (config) => {
    return withProjectBuildGradle(config, (modConf) => {
      const content = modConf.modResults.contents.replace(
        "maven { url 'https://www.jitpack.io' }",
        `maven { url 'https://www.jitpack.io' }\nmaven { url 'https://maven.mapsindoors.com/' }`
      );
  
      modConf.modResults.contents = content;
      return modConf;
    });
  };
  
  /**
   * Plugin to add google map api key to String.xml
   * @param {*} apiKey api key to set
   * @param {*} config expo project config
   * @returns modified config
   */
  const withGoogleAPIKey = (apiKey) => (config) => {
    return withStringsXml(config, (modConf) => {
      modConf.modResults = AndroidConfig.Strings.setStringItem(
        [{ _: apiKey, $: { name: "google_maps_key", translatable: "false" } }],
        modConf.modResults
      );
  
      return modConf;
    });
  };
  
  /**
   * Plugin to add google map api key to String.xml
   * @param {*} config expo project config
   * @returns modified config
   */
  const withManifest = (config) => {
    return withAndroidManifest(config, (modConf) => {
      const app = modConf.modResults.manifest.application;
  
      if (app[0] && app[0]["meta-data"]) {
        app[0]["meta-data"].push({
          $: {
            "android:name": "com.google.android.maps.v2.API_KEY",
            "android:value": "@string/google_maps_key",
          },
        });
      }
  
      modConf.modResults.manifest.application = app;
      return modConf;
    });
  };
  
  /**
   * Plugin to add google map init
   * @param {*} config expo project config
   * @param {*} {apiKey} api key to set
   * @returns modified config
   */
  const withGoogleMapIOSSetup = (apiKey) => (config) => {
    return withAppDelegate(config, (modConf) => {
      let contents = modConf.modResults.contents;
  
      contents = contents.replace(
        `#import "AppDelegate.h"`,
        `#import "AppDelegate.h"\n\n#import "GoogleMaps/GoogleMaps.h"`
      );
  
      contents = contents.replace(
        `self.moduleName = @"main";`,
        `[GMSServices provideAPIKey:@"${apiKey}"];\n\nself.moduleName = @"main";`
      );
  
      modConf.modResults.contents = contents;
      return modConf;
    });
  };
  
  /**
   * Combine Expo configuration and properties
   * @param {*} config expo project config
   * @param {*} {apiKey} api key to set
   * @returns modified config
   */
  const withMapsPeople = (config, { apiKeyAndroid, apiKeyIOS }) => {
    const plugins = [
      withMavenRepo,
      withGoogleAPIKey(apiKeyAndroid),
      withManifest,
      withGoogleMapIOSSetup(apiKeyIOS),
    ];
  
    return withPlugins(config, plugins);
  };
  
  module.exports = withMapsPeople;