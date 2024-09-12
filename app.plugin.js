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
   * Plugin to add podfile script
   * @param {*} config expo project config
   * @returns modified config
   */
  const withPodfile = (config) => {
    return withDangerousMod(config, [
      "ios",
      (modConf) => {
        const podfile = path.join(
          modConf.modRequest.platformProjectRoot,
          "Podfile"
        );
  
        const contents = fs.readFileSync(podfile, "utf-8");
  
        let updated = `PROJECT_ROOT_DIR = File.dirname(File.expand_path(__FILE__))
    PODS_DIR = File.join(PROJECT_ROOT_DIR, 'Pods')
    PODS_TARGET_SUPPORT_FILES_DIR = File.join(PODS_DIR, 'Target Support Files')
          
    ${contents}
          
    # CocoaPods provides the abstract_target mechanism for sharing dependencies between distinct targets.
    # However, due to the complexity of our project and use of shared frameworks, we cannot simply bundle everything under
    # a single abstract_target. Using a pod in a shared framework target and an app target will cause CocoaPods to generate
    # a build configuration that links the pod's frameworks with both targets. This is not an issue with dynamic frameworks,
    # as the linker is smart enough to avoid duplicate linkage at runtime. Yet for static frameworks the linkage happens at
    # build time, thus when the shared framework target and app target are combined to form an executable, the static
    # framework will reside within multiple distinct address spaces. The end result is duplicated symbols, and global
    # variables that are confined to each target's address space, i.e not truly global within the app's address space.
    
    def remove_static_framework_duplicate_linkage(static_framework_pods)
      puts "Removing duplicate linkage of static frameworks"
      
      Dir.glob(File.join(PODS_TARGET_SUPPORT_FILES_DIR, "Pods-*")).each do |path|
        pod_target = path.split('-', -1).last
        
        static_framework_pods.each do |target, pods|
          next if pod_target == target
          frameworks = pods.map { |pod| identify_frameworks(pod) }.flatten
          
          Dir.glob(File.join(path, "*.xcconfig")).each do |xcconfig|
            lines = File.readlines(xcconfig)
            
            if other_ldflags_index = lines.find_index { |l| l.start_with?('OTHER_LDFLAGS') }
              other_ldflags = lines[other_ldflags_index]
              
              frameworks.each do |framework|
                other_ldflags.gsub!("-framework \\"#{framework}\\"", '')
              end
              
              File.open(xcconfig, 'w') do |fd|
                fd.write(lines.join)
              end
            end
          end
        end
      end
    end
    
    def identify_frameworks(pod)
      frameworks = Dir.glob(File.join(PODS_DIR, pod, "**/*.framework")).map { |path| File.basename(path) }
      
      if frameworks.any?
        return frameworks.map { |f| f.split('.framework').first }
      end
      
      return pod
    end`;
  
    updated = updated.replace(
      `expo_patch_react_imports!(installer)`,
      `expo_patch_react_imports!(installer)\nremove_static_framework_duplicate_linkage({'MapsIndoorsGoogleMaps' => ['GoogleMaps']})`
    );
  
        fs.writeFileSync(podfile, updated, "utf8");
  
        return modConf;
      },
    ]);
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
      withPodfile,
    ];
  
    return withPlugins(config, plugins);
  };
  
  module.exports = withMapsPeople;