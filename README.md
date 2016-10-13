# Cordova Health Plugin

A plugin that abstracts fitness and health repositories like Apple HealthKit or Google Fit.

This work is based on [cordova plugin googlefit](https://github.com/2dvisio/cordova-plugin-googlefit) and on [cordova healthkit plugin](https://github.com/Telerik-Verified-Plugins/HealthKit)

For an introduction about Google Fit versus HealthKit see [this very good article](https://yalantis.com/blog/how-can-healthkit-and-googlefit-help-you-develop-healthcare-and-fitness-apps/).

## Warning

This plugin stores health data in Google Fit, practice that is [discouraged by Google](https://developers.google.com/fit/terms).

## Installation

Just execute this line in your project's folder:

```
cordova plugin add cordova-plugin-health
```

## Requirements for iOS apps

* Make sure your app id has the 'HealthKit' entitlement when this plugin is installed (see iOS dev center).
* Also, make sure your app and AppStore description complies with these Apple review guidelines: https://developer.apple.com/app-store/review/guidelines/#healthkit
* There are [two keys](https://developer.apple.com/library/content/documentation/General/Reference/InfoPlistKeyReference/Articles/CocoaKeys.html#//apple_ref/doc/uid/TP40009251-SW48) to the info.plist file, `NSHealthShareUsageDescription` and `NSHealthUpdateUsageDescription` that are assigned with an empty string by default by the plugin. You may want to put a better description there.

## Requirements for Android apps

* You need to have the Google Services API downloaded in your SDK
* Be sure to give your app access to the Google Fitness API, see [this](https://developers.google.com/fit/android/get-api-key) and [this](https://github.com/2dvisio/cordova-plugin-googlefit#sdk-requirements-for-compiling-the-plugin)
* If you are wondering what key your compiled app is using, you can type `keytool -list -printcert -jarfile yourapp.apk`

Some more detailed instructions are provided [here](https://github.com/2dvisio/cordova-plugin-googlefit)


## Supported data types

As HealthKit does not allow adding custom data types, only a subset of data types supported by HealthKit has been chosen.
Google Fit is limited to fitness data and, for health, custom data types are defined with the suffix of the package name of your project.

| data type       |      HealthKit equivalent (unit)                        |  Google Fit equivalent                   |
|-----------------|---------------------------------------------------------|------------------------------------------|
| steps           | HKQuantityTypeIdentifierStepCount (count)               | TYPE_STEP_COUNT_DELTA                    |
| distance        | HKQuantityTypeIdentifierDistanceWalkingRunning (m) + HKQuantityTypeIdentifierDistanceCycling (m) | TYPE_DISTANCE_DELTA |
| calories        | HKQuantityTypeIdentifierActiveEnergyBurned + HKQuantityTypeIdentifierBasalEnergyBurned(kcal) | TYPE_CALORIES_EXPENDED |
| calories.active | HKQuantityTypeIdentifierActiveEnergyBurned (kcal)       | TYPE_CALORIES_EXPENDED - (TYPE_BASAL_METABOLIC_RATE * time window) |
| calories.basal  | HKQuantityTypeIdentifierBasalEnergyBurned (kcal)        | TYPE_BASAL_METABOLIC_RATE * time window  |
| activity        | HKWorkoutTypeIdentifier + HKCategoryTypeIdentifierSleepAnalysis | TYPE_ACTIVITY_SEGMENT            |
| height          | HKQuantityTypeIdentifierHeight (m)                      | TYPE_HEIGHT                              |
| weight          | HKQuantityTypeIdentifierBodyMass (kg)                   | TYPE_WEIGHT                              |
| heart_rate      | HKQuantityTypeIdentifierHeartRate (count/min)           | TYPE_HEART_RATE_BPM                      |
| fat_percentage  | HKQuantityTypeIdentifierBodyFatPercentage (%)           | TYPE_BODY_FAT_PERCENTAGE                 |
| gender          | HKCharacteristicTypeIdentifierBiologicalSex             | custom (YOUR_PACKAGE_NAME.gender)        |
| date_of_birth   | HKCharacteristicTypeIdentifierDateOfBirth               | custom (YOUR_PACKAGE_NAME.date_of_birth) |


Note: units of measurements are fixed !

Returned objects can be of different types, see examples below:

| data type      | value                             |
|----------------|-----------------------------------|
| steps          | 34                                |
| distance       | 101.2                             |
| calories       | 245.3                             |
| activity       | "walking"  (note: recognised activities and their mapping to Fit / HealthKit equivalents are listed in [this file](activities_map.md)) |
| height         | 185.9                             |
| weight         | 83.3                              |
| heart_rate     | 66                                |
| fat_percentage | 31.2                              |
| gender         | "male"                            |
| date_of_birth  | { day: 3, month: 12, year: 1978 } |


## Methods

### isAvailable()

Tells if either Google Fit or HealthKit are available.

```
navigator.health.isAvailable(successCallback, errorCallback)
```

- successCallback: {type: function(available)}, if available a true is passed as argument, false otherwise
- errorCallback: {type: function(err)}, called if something went wrong, err contains a textual description of the problem

Quirks of isAvailable()

- In Android, it checks both if recent Google Play Services and Google Fit are installed. If the play services are not installed, or are obsolete, it will show a pop-up suggesting to download them. If Google Fit is not installed, it will open the Play Store at the location of the Google Fit app. The plugin does not wait until the missing packages are installed, it will return immediately.

### requestAuthorization()

Requests read and write access to a set of data types.
It is recommendable to always explain why the app needs access to the data before asking the user to authorise it.

This function must be called before using the query and store functions, even if the authorisation has already been given at some point in the past.

```
navigator.health.requestAuthorization(datatypes, successCallback, errorCallback)
```

- datatypes: {type: Array of String}, a list of data types you want to be granted access to
- successCallback: {type: function}, called if all OK
- errorCallback: {type: function(err)}, called if something went wrong, err contains a textual description of the problem

Quirks of requestAuthorization()

- In Android, it will try to get authorisation from the Google Fit APIs. It is necessary that the app's package name and the signing key are registered in the Google API console (see [here](https://developers.google.com/fit/android/get-api-key)).
- In Android, be aware that if the activity is destroyed (e.g. after a rotation) or is put in background, the connection to Google Fit may be lost without any callback. Going through the autorisation will ensure that the app is connected again.
- In Android 6 and over, this function will also ask for some dynamic permissions if needed (e.g. in the case of "distance", it will need access to ACCESS_FINE_LOCATION).

### query()

Gets all the records of a certain data type within a certain time window.

Warning: it can generate long arrays!

```
navigator.health.query({
        startDate: new Date(new Date().getTime() - 3 * 24 * 60 * 60 * 1000), // three days ago
        endDate: new Date(), // now
        dataType: 'height'
        }, successCallback, errorCallback)
```

- startDate: {type: Date}, start date from which to get data
- endDate: {type: Date}, end data to which to get the data
- dataType: {type: String}, the data type to be queried (see above)
- successCallback: {type: function(data) }, called if all OK, data contains the result of the query in the form of an array of: { startDate: Date, endDate: Date, value: xxx, unit: 'xxx', sourceName: '', sourceBundleId: '' }
- errorCallback: {type: function(err)}, called if something went wrong, err contains a textual description of the problem


Quirks of query()

- in Google Fit calories.basal is returned as an average per day, and usually is not available in all days (may be not available in time windows smaller than 5 days or more)
- in Google Fit calories.active is computed by subtracting the basal from the total, as basal an average of the a number of days before endDate is taken (the actual number is defined in a variable, currently set to 7)
- while Google Fit calculates basal and active calories automatically, HealthKit needs an explicit input
- when querying for activities, Google Fit is able to determine some activities automatically, while HealthKit only relies on the input of the user or of some external app
- when querying for activities, calories and distance are also provided in HealthKit (units are kcal and metres) and never in Google Fit


### queryAggregated()

Gets aggregated data in a certain time window.
Usually the sum is returned for the given quantity.

```
navigator.health.queryAggregated({
        startDate: new Date(new Date().getTime() - 3 * 24 * 60 * 60 * 1000), // three days ago
        endDate: new Date(), // now
        dataType: 'steps',
        bucket: 'day'
        }, successCallback, errorCallback)
```

- startDate: {type: Date}, start date from which to get data
- endDate: {type: Date}, end data to which to get the data
- dataType: {type: String}, the data type to be queried (see below for supported data types)
- bucket: {type: String}, if specified, aggregation is grouped an array of "buckets" (windows of time), supported values are: 'hour', 'day', 'week', 'month', 'year'
- successCallback: {type: function(data)}, called if all OK, data contains the result of the query, see below for returned data types
- errorCallback: {type: function(err)}, called if something went wrong, err contains a textual description of the problem

Not all data types are supported for aggregated queries.
The following table shows what types are supported and examples of aggregated data:

| data type       | example of returned object |
|-----------------|----------------------------|
| steps           | { startDate: Date, endDate: Date, value: 5780, unit: 'count' } |
| distance        | { startDate: Date, endDate: Date, value: 12500.0, unit: 'm' } |
| calories        | { startDate: Date, endDate: Date, value: 25698.1, unit: 'kcal' } |
| calories.active | { startDate: Date, endDate: Date, value: 3547.4, unit: 'kcal' } |
| calories.basal  | { startDate: Date, endDate: Date, value: 13146.1, unit: 'kcal' } |
| activity        | { startDate: Date, endDate: Date, value: { still: { duration: 520000, calories: 30, distance: 0 }, walking: { duration: 223000, calories: 20, distance: 15 }}, unit: 'activitySummary' } (note: duration is expressed in milliseconds, distance in metres and calories in kcal) |

Quirks of queryAggregated()

- when querying for activities, calories and distance are provided when available in HealthKit and never in Google Fit
- in Android, the start and end dates returned are the date of the first and the last available samples. If no samples are found, start and end may not be set.

### store()

Stores a data point.

```
navigator.health.store({
	startDate:  new Date(new Date().getTime() - 3 * 60 * 1000), // three minutes ago
	endDate: new Date(),
	dataType: 'steps',
	value: 180,
	sourceName: 'my_app',
	sourceBundleId: 'com.example.my_app' }, successCallback, errorCallback)
```

- startDate: {type: Date}, start date from which to get data
- endDate: {type: Date}, end data to which to get the data
- dataType: {type: a String}, the data type
- value: {type: a number or an Object}, depending on the actual data type
- sourceName: {type: String}, the source that produced this data. In iOS this is ignored and set automatically to the name of your app.
- sourceBundleId: {type: String}, the complete package of the source that produced this data. In Android, if not specified, it's assigned to the package of the App. In iOS this is ignored and set automatically to the bunde id of the app.
- successCallback: {type: function}, called if all OK
- errorCallback: {type: function(err)}, called if something went wrong, err contains a textual description of the problem


Quirks of store()

- in iOS you cannot store the total calories, you need to specify either basal or active. If you use total calories, the active ones will be stored.
- in Android you can only store active calories, as the basal are estimated automatically. If you store total calories, these will be treated as active.
- in iOS distance is assumed to be of type WalkingRunning, if you want to explicitly set it to Cycling you need to add the field ` cycling: true `.
- in iOS, storing the sleep activities is not supported at the moment.

## Differences between HealthKit and Google Fit

* HealthKit includes medical data (eg blood glucose), Google Fit is currently only related to fitness data
* HealthKit provides a data model that is not extensible, Google Fit allows defining custom data types
* HealthKit allows to insert data with the unit of measurement of your choice, and automatically translates units when quiered, Google Fit stores data with a fixed unit of measurement
* HealthKit automatically counts steps and distance when you carry your phone with you, Google Fit also detects the kind of activity (sedentary, running, walking, cycling, in vehicle)
* HealthKit automatically computes the distance only for running/walking activities, Google Fit includes bicycle also


## External Resources

* The official Apple documentation for [HealthKit can be found here](https://developer.apple.com/library/ios/documentation/HealthKit/Reference/HealthKit_Framework/index.html#//apple_ref/doc/uid/TP40014707).
* For functions that require the `unit` attribute, you can find the comprehensive list of possible units from the [Apple Developers documentation](https://developer.apple.com/library/ios/documentation/HealthKit/Reference/HKUnit_Class/index.html#//apple_ref/doc/uid/TP40014727-CH1-SW2).
* [HealthKit constants](https://developer.apple.com/library/ios/documentation/HealthKit/Reference/HealthKit_Constants/index.html), used throughout the code
* Google Fit [supported data types](https://developers.google.com/fit/android/data-types)

## Roadmap

short term

- add query with buckets (see window.plugins.healthkit.querySampleTypeAggregated for HealthKit)
- add delete
- get steps from the "polished" Google Fit data source (see https://plus.google.com/104895513165544578271/posts/a8P62A6ejQy)
- add support for HKCategory samples in HealthKit
- extend the datatypes
 - blood pressure  (KCorrelationTypeIdentifierBloodPressure, custom data type)
 - food (HKCorrelationTypeIdentifierFood, TYPE_NUTRITION)
 - blood glucose
 - location (NA, TYPE_LOCATION)

long term

- add registration to updates (in Fit:  HistoryApi#registerDataUpdateListener() )
- store vital signs on an encrypted DB in the case of Android and remove custom datatypes (possible choice: [sqlcipher](https://www.zetetic.net/sqlcipher/sqlcipher-for-android/). The file would be stored on shared drive, and it would be shared among apps through a service. You could more simply share the file, but then how would you share the password? If shared through a service, all apps would have the same service because it's part of the plugin, so the service should not auto-start until the first app tries to bind it (see [this](http://stackoverflow.com/questions/31506177/the-same-android-service-instance-for-two-apps) for suggestions). This is sub-optimal, as all apps would have the same copy of the service (although lightweight). A better approach would be requiring an extra app, but this creates other issues like "who would publish it?", "why the user would be needed to download another app?" etc.
- add also Samsung Health as a health record for Android

## Contributions

Any help is more than welcome!
I cannot program in iOS, so I would particularly appreciate someone who can give me a hand.
Just send me an email to my_username at gmail.com
