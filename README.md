# react-native-maps-indoors-google-maps

## Documentation

Visit [our reference document site](https://app.mapsindoors.com/mapsindoors/reference/react-native/google-maps/1.0.8/index.html) to get an overview of what the MapsIndoors SDK offers.

## Getting started

`$ npm install @mapsindoors/react-native-maps-indoors-google-maps`

### iOS

The MapsIndoors SDK requires iOS 13, so make sure that your podfile is configured for iOS 13

```pod
platform :ios, '13.0
```

#### Providing API key

1. Navigate to `iOS/MyApp/AppDelegate.m`.
2. Import `#import "GoogleMaps/GoogleMaps.h"` on the class.
3. Add this code as the first line inside the application function: `[GMSServices provideAPIKey:@"YOUR GOOGLE MAPS API KEY HERE"];`

#### Adding MapsIndoors script specific to Google Maps, to Podfile

After this you should navigate into the iOS folder of your flutter project and add this script to the applications Podfile: [MapsIndoors podfile Post install](https://github.com/MapsIndoors/MapsIndoorsIOS/wiki/Podfile-post_install-v4)

### Android

#### Android Gooogle Maps Setup

​
To get the underlying Google Map to function, you need to perform the following steps:
​

1. Navigate to `android/app/src/main/res/value`.
2. Create a file in this folder called `google_maps_api_key.xml`.
3. Copy and paste the below code snippet and replace `YOUR_KEY_HERE` with your Google Maps API key.
4. Add the key to your application metadata in the manifest, located at `android/app/src/main/AndroidManifest.xml`
​

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="google_maps_key">YOUR_KEY_HERE</string>
</resources>
```

```xml
<application>
    ...
    <meta-data
            android:name="com.google.android.maps.v2.API_KEY"
            android:value="@string/google_maps_key" />
    ...
</application>
```

#### MapsIndoors Gradle Setup

​
The plugin Gradle project has trouble resolving the MapsIndoors dependency, so to ensure that it is resolved correctly, do the following:
​

1. Navigate to the app's project level `build.gradle`.
2. add `maven { url 'https://maven.mapsindoors.com/' }` to `allprojects`/`repositories` after `mavenCentral()`
​

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://maven.mapsindoors.com/' }
    }
}
```

## Usage

### Showing your map

This snippet shows you how to set up MapsIndoors in a React Native application.

```javascript
import MapsIndoors, { MapControl, MapView } from 'react-native-maps-indoors';
...
//Function to initialize mapsindoors and mapcontrol. To load a solution and show data onto the map.
const loadMapsIndoors = () => {
    //Load solution data with your api key
    MapsIndoors.load('API_KEY').then(async () => {
        //Create the MapControl. Which will be using the MapView of the component.
        const mc = await MapControl.create(new MPMapConfig({useDefaultMapsIndoorsStyle: true}), NativeEventEmitter);
        //Get a venue and move the camera to it.
        let venue: MPVenue = (await MapsIndoors.getVenues()).getAll()[0];
        mapControl.goTo(venue);
    }
}

...
render() {
  return (
    <MapView
        style={{
            width: Dimensions.get('window').width,
            height: Dimensions.get('window').height,
        }}
    />
  );
}
...
```

### Showing a route

```javascript
  const showRoute = async () => {
    let point = new MPPoint(57.0580431, 9.9505475);
    let point2 = new MPPoint(57.0581638, 9.9507732, 10);
    var directionsService = await MPDirectionsService.create();

    //Optional query parameters for the route.
    directionsService.setIsDeparture(true);
    directionsService.setTime(Date.now());
    directionsService.setTravelMode('bicycling');

    var route = await directionsService.getRoute(point, point2);
    directionsRenderer = new MPDirectionsRenderer(NativeEventEmitter);
    directionsRenderer.setRoute(route);
  };
```

### Searching locations

This code snippet shows a function called `searchForParking` that takes a single argument of type `MPPoint`. The function uses `MapsIndoors` to search for locations matching the query string `"parking"` near the point specified, and uses the filter to only get the first 10 matches.
​
It mathces in the locations' descriptions, names, and external IDs to the query string. Once the search is complete, it is possible to update/get information from the locations (not specified in the code snippet).

```javascript
const searchForParking = async (point: MPPoint) => {
    let query = MPQuery.create({query: "parking",
                                near: point,
                                queryProperties: [MPLocationPropertyNames.description, MPLocationPropertyNames.name, MPLocationPropertyNames.externalId]});
    let filter = MPFilter.create({take: 10});
    let parkingLotsNearPoint = await MapsIndoors.getLocationsAsync(query, filter);
}
```

### Changing the look with DisplayRules

​
This code snippet shows three ways to manipulate display rules in the MapsIndoors SDK.
​
The `hideLocationsByDefault` method hides all markers that are not explicitly visible by setting the main display rule to not visible.
​
The `showLocationsByDefault` method ensures all markers are shown by setting the main display rule to visible.
​
The `changeTypePolygonColor(String type, String color)` method changes the fill color for all polygons belonging to a specific type. It gets the display rule for the specified type using `getDisplayRuleByName`, and sets the fill color using `setPolygonFillColor`.
​
These methods can all be used to customize the display of markers and polygons on the map.
​

```javascript
// This method changes the main display rule to hide all markers,
// This will cause all locations/types that are not explicitly visible to be hidden.
const hideLocationsByDefault = async () => {
    let mainDisplayRule = await MapsIndoors.getMainDisplayRule();
    mainDisplayRule?.setVisible(false);
}
​
// This method changes the main display rule to show all markers,
// This will cause all locations/types that are not explicitly visible to be shown.
const showLocationsByDefault = async () => {
    let mainDisplayRule = await MapsIndoors.getMainDisplayRule();
    mainDisplayRule?.setVisible(true);
}
​
// This method changes the fill color for all polygons belonging to a specific [type]
// the color MUST be a valid hex color string.
const changeTypePolygonColor = async (type: string, color: string) => {
    let typeDisplayRule = await MapsIndoors.getDisplayRuleByName(type);
    typeDisplayRule?.setPolygonFillColor(color);
}
```
