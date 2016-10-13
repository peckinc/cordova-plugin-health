var exec = require('cordova/exec');

var Health = function () {
  this.name = 'health';
};

var dataTypes = [];
dataTypes['steps'] = 'HKQuantityTypeIdentifierStepCount';
dataTypes['distance'] = 'HKQuantityTypeIdentifierDistanceWalkingRunning'; // and HKQuantityTypeIdentifierDistanceCycling
dataTypes['calories'] = 'HKQuantityTypeIdentifierActiveEnergyBurned'; // and HKQuantityTypeIdentifierBasalEnergyBurned
dataTypes['calories.active'] = 'HKQuantityTypeIdentifierActiveEnergyBurned';
dataTypes['calories.basal'] = 'HKQuantityTypeIdentifierBasalEnergyBurned';
dataTypes['height'] = 'HKQuantityTypeIdentifierHeight';
dataTypes['weight'] = 'HKQuantityTypeIdentifierBodyMass';
dataTypes['heart_rate'] = 'HKQuantityTypeIdentifierHeartRate';
dataTypes['fat_percentage'] = 'HKQuantityTypeIdentifierBodyFatPercentage';
dataTypes['activity'] = 'HKWorkoutTypeIdentifier'; // and HKCategoryTypeIdentifierSleepAnalysis

var units = [];
units['steps'] = 'count';
units['distance'] = 'm';
units['calories'] = 'kcal';
units['calories.active'] = 'kcal';
units['calories.basal'] = 'kcal';
units['height'] = 'm';
units['weight'] = 'kg';
units['heart_rate'] = 'count/min';
units['fat_percentage'] = '%';

Health.prototype.isAvailable = function (success, error) {
  window.plugins.healthkit.available(success, error);
};

Health.prototype.requestAuthorization = function (dts, onSuccess, onError) {
  var HKdatatypes = [];
  for (var i = 0; i < dts.length; i++) {
    if ((dts[i] !== 'gender') && (dts[i] !== 'date_of_birth')) { // ignore gender and DOB
      if (dataTypes[dts[i]]) {
        HKdatatypes.push(dataTypes[dts[i]]);
        if (dts[i] === 'distance') HKdatatypes.push('HKQuantityTypeIdentifierDistanceCycling');
        if (dts[i] === 'activity') HKdatatypes.push('HKCategoryTypeIdentifierSleepAnalysis');
        if (dts[i] === 'calories') HKdatatypes.push('HKQuantityTypeIdentifierBasalEnergyBurned');
      } else {
        onError('unknown data type ' + dts[i]);
        return;
      }
    }
  }
  if (HKdatatypes.length) {
    window.plugins.healthkit.requestAuthorization({
      'readTypes': HKdatatypes,
      'writeTypes': null // peckinc, no writing HKdatatypes
    }, onSuccess, onError);
  } else onSuccess();
};

Health.prototype.query = function (opts, onSuccess, onError) {
  var startD = opts.startDate;
  var endD = opts.endDate;

  if (opts.dataType === 'gender') {
    window.plugins.healthkit.readGender(function (data) {
      var res = [];
      res[0] = {
        startDate: startD,
        endDate: endD,
        value: data,
        sourceName: 'Health',
        sourceBundleId: 'com.apple.Health'
      };
      onSuccess(res);
    }, onError);
  } else if (opts.dataType === 'date_of_birth') {
    window.plugins.healthkit.readDateOfBirth(function (data) {
      data.startDate = startD;
      data.endDate = endD;
      var res = [];
      var date = new Date(data);
      res[0] = {
        startDate: opts.startDate,
        endDate: opts.endDate,
        value: { day: date.getDate(), month: date.getMonth() + 1, year: date.getFullYear() },
        sourceName: 'Health',
        sourceBundleId: 'com.apple.Health'
      };
      onSuccess(res);
    }, onError);
  } else if (opts.dataType === 'activity') {
    // opts is not really used, the plugin just returns ALL workouts
    window.plugins.healthkit.findWorkouts(opts, function (data) {
      var result = [];
      for (var i = 0; i < data.length; i++) {
        var res = {};
        res.startDate = new Date(data[i].startDate);
        res.endDate = new Date(data[i].endDate);
        // filter the results based on the dates
        if ((res.startDate >= opts.startDate) && (res.endDate <= opts.endDate)) {
          res.value = data[i].activityType;
          res.unit = 'activityType';
          res.calories = parseInt(data[i].energy.slice(0, -2)); // remove the ending J
          res.distance = parseInt(data[i].distance);
          res.sourceName = data[i].sourceName;
          res.sourceBundleId = data[i].sourceBundleId;
          result.push(res);
        }
      }
      // get sleep analysis also
      opts.sampleType = 'HKCategoryTypeIdentifierSleepAnalysis';
      window.plugins.healthkit.querySampleType(opts, function (data) {
        for (var i = 0; i < data.length; i++) {
          var res = {};
          res.startDate = new Date(data[i].startDate);
          res.endDate = new Date(data[i].endDate);
          if (data[i].value == 0) res.value = 'sleep.awake';
          else res.value = 'sleep';
          res.unit = 'activityType';
          res.sourceName = data[i].sourceName;
          res.sourceBundleId = data[i].sourceBundleId;
          result.push(res);
        }
        onSuccess(result);
      }, onError);
    }, onError);
  } else if (dataTypes[ opts.dataType ]) {
    opts.sampleType = dataTypes[ opts.dataType ];
    if (units[ opts.dataType ]) {
      opts.unit = units[ opts.dataType ];
    }
    window.plugins.healthkit.querySampleType(opts, function (data) {
      var result = [];
        var convertSamples = function (samples) {
          for (var i = 0; i < samples.length; i++) {
            var res = {};
            res.startDate = new Date(samples[i].startDate);
            res.endDate = new Date(samples[i].endDate);
            res.value = samples[i].quantity;
            if (data[i].unit) res.unit = samples[i].unit;
            else if (opts.unit) res.unit = opts.unit;
            res.sourceName = samples[i].sourceName;
            res.sourceBundleId = samples[i].sourceBundleId;
            result.push(res);
          }
        };
        convertSamples(data);
        if(opts.dataType == 'distance'){ //in the case of the distance, add the cycling distances
          
          // peckinc, no bikes, opts.sampleType = 'HKQuantityTypeIdentifierDistanceCycling';
          
          //reassing start and end times
          opts.startDate = startD;
          opts.endDate = endD;
          window.plugins.healthkit.querySampleType(opts, function (data) {
            convertSamples(data);
            onSuccess(result);
          }, onError);
        } else if (opts.dataType === 'calories') { // in the case of the calories, add the basal
          opts.sampleType = 'HKQuantityTypeIdentifierBasalEnergyBurned';
          opts.startDate = startD;
          opts.endDate = endD;
          window.plugins.healthkit.querySampleType(opts, function (data) {
            convertSamples(data);
            onSuccess(result);
          }, onError);
        } else onSuccess(result);
    }, onError); // first call to querySampleType
  } else {
    onError('unknown data type ' + opts.dataType);
  }
};

Health.prototype.queryAggregated = function (opts, onSuccess, onError) {
  if ((opts.dataType !== 'steps') && (opts.dataType !== 'distance') &&
  (opts.dataType !== 'calories') && (opts.dataType !== 'calories.active') &&
  (opts.dataType !== 'calories.basal') && (opts.dataType !== 'activity')) {
    // unsupported datatype
    onError('Datatype ' + opts.dataType + ' not supported in queryAggregated');
    return;
  }
  var startD = opts.startDate;
  var endD = opts.endDate;
  opts.sampleType = dataTypes[ opts.dataType ];
  if (units[ opts.dataType ]) opts.unit = units[ opts.dataType ];
  if (opts.bucket) {
    // ----- with buckets
    opts.aggregation = opts.bucket;
    opts.startDate = startD;
    opts.endDate = endD;
    if (opts.dataType === 'activity') {
      // query and manually aggregate
      navigator.health.query(opts, function (data) {
        // aggregate by period and activity
        var retval = [];
        // create buckets
        var sd;
        if (opts.bucket === 'hour') {
          sd = new Date(startD.getFullYear(), startD.getMonth(), startD.getDate(), startD.getHours());
        } else if (opts.bucket === 'day') {
          sd = new Date(startD.getFullYear(), startD.getMonth(), startD.getDate());
        } else if (opts.bucket === 'week') {
          sd = new Date(startD.getTime());
          sd.setDate(startD.getDate() - (startD.getDay() === 0 ? 6 : startD.getDay() - 1)); // last monday
        } else if (opts.bucket === 'month') {
          sd = new Date(startD.getFullYear(), startD.getMonth());
        } else if (opts.bucket === 'year') {
          sd = new Date(startD.getFullYear());
        } else {
          onError('Bucket not recognised ' + opts.bucket);
          return;
        }
        while (sd <= endD) {
          var ed;
          if (opts.bucket === 'hour') {
            ed = new Date(sd.getFullYear(), sd.getMonth(), sd.getDate(), sd.getHours() + 1);
          } else if (opts.bucket === 'day') {
            ed = new Date(sd.getFullYear(), sd.getMonth(), sd.getDate() + 1);
          } else if (opts.bucket === 'week') {
            ed = new Date(sd.getFullYear(), sd.getMonth(), sd.getDate() + 7);
          } else if (opts.bucket === 'month') {
            ed = new Date(sd.getFullYear(), sd.getMonth() + 1);
          } else if (opts.bucket === 'year') {
            ed = new Date(sd.getFullYear() + 1);
          }
          retval.push({
            startDate: sd,
            endDate: ed,
            value: {},
            unit: 'activitySummary'
          });
          sd = ed;
        }
        for (var i = 0; i < data.length; i++) {
          // select the bucket
          for (var j = 0; j < retval.length; j++) {
            if ((data[i].endDate <= retval[j].endDate) && (data[i].startDate >= retval[j].startDate)) {
              // add the sample to the bucket
              var dur = (data[i].endDate - data[i].startDate);
              var dist = data[i].distance;
              var cals = data[i].calories;
              if (retval[j].value[data[i].value]) {
                retval[j].value[data[i].value].duration += dur;
                retval[j].value[data[i].value].distance += dist;
                retval[j].value[data[i].value].calories += cals;
              } else {
                retval[j].value[data[i].value] = {
                  duration: dur,
                  distance: dist,
                  calories: cals
                };
              }
            }
          }
        }
        onSuccess(retval);
      }, onError);
    } else {
      window.plugins.healthkit.querySampleTypeAggregated(opts, function (value) {
        // merges values and adds unit
        var mergeAndSuccess = function (value, previous) {
          var retval = [];
          for (var i = 0; i < value.length; i++) {
            var sample = {
              startDate: value[i].startDate,
              endDate: value[i].endDate,
              value: value[i].quantity,
              unit: opts.unit
            };
            for (var j = 0; j < previous.length; j++) {
              // we expect the buckets to have the same start and end dates
              if (value[i].startDate === rundists[j].startDate) {
                value[i].value += previous[j].quantity;
              }
            }
            retval.push(sample);
          }
          onSuccess(retval);
        };
        if (opts.dataType === 'distance') {
          // add cycled distance
          var rundists = value;
          opts.sampleType = 'HKQuantityTypeIdentifierDistanceCycling';
          opts.startDate = startD;
          opts.endDate = endD;
          window.plugins.healthkit.querySampleTypeAggregated(opts, function (v) {
            mergeAndSuccess(v, rundists);
          }, onError);
        } else if (opts.dataType === 'calories') {
          // add basal calories
          var activecals = value;
          opts.sampleType = 'HKQuantityTypeIdentifierBasalEnergyBurned';
          opts.startDate = startD;
          opts.endDate = endD;
          window.plugins.healthkit.sumQuantityType(opts, function (v) {
            mergeAndSuccess(v, activecals);
          }, onError);
        } else {
          // refactor objects
          var retval = [];
          for (var i = 0; i < value.length; i++) {
            var sample = {
              startDate: value[i].startDate,
              endDate: value[i].endDate,
              value: value[i].quantity,
              unit: opts.unit
            };
            retval.push(sample);
          }
          onSuccess(retval);
        }
      }, onError);
    }
  } else {
    // ---- no bucketing, just sum
    if (opts.dataType === 'activity') {
      var res = {
        startDate: startD,
        endDate: endD,
        value: {},
        unit: 'activitySummary'
      };
      navigator.health.query(opts, function (data) {
        // aggregate by activity
        for (var i = 0; i < data.length; i++) {
          var dur = (data[i].endDate - data[i].startDate);
          var dist = data[i].distance;
          var cals = data[i].calories;
          if (res.value[data[i].value]) {
            res.value[data[i].value].duration += dur;
            res.value[data[i].value].distance += dist;
            res.value[data[i].value].calories += cals;
          } else {
            res.value[data[i].value] = {
              duration: dur,
              distance: dist,
              calories: cals
            };
          }
        }
        onSuccess(res);
      }, onError);
    } else {
      window.plugins.healthkit.sumQuantityType(opts, function (value) {
        if (opts.dataType === 'distance') {
          // add cycled distance
          var dist = value;
          opts.sampleType = 'HKQuantityTypeIdentifierDistanceCycling';
          opts.startDate = startD;
          opts.endDate = endD;
          window.plugins.healthkit.sumQuantityType(opts, function (value) {
            onSuccess({
              startDate: startD,
              endDate: endD,
              value: value + dist,
              unit: opts.unit
            });
          }, onError);
        } else if (opts.dataType === 'calories') {
          // add basal calories
          var activecals = value;
          opts.sampleType = 'HKQuantityTypeIdentifierBasalEnergyBurned';
          opts.startDate = startD;
          opts.endDate = endD;
          window.plugins.healthkit.sumQuantityType(opts, function (basalcals) {
            onSuccess({
              startDate: startD,
              endDate: endD,
              value: basalcals + activecals,
              unit: opts.unit
            });
          }, onError);
        } else {
          onSuccess({
            startDate: startD,
            endDate: endD,
            value: value,
            unit: opts.unit
          });
        }
      }, onError);
    }
  }
};

Health.prototype.store = function (data, onSuccess, onError) {
  if (data.dataType === 'gender') {
    onError('Gender is not writeable');
  } else if (data.dataType === 'date_of_birth') {
    onError('Date of birth is not writeable');
  } else if (data.dataType === 'activity') {
    // sleep activity, needs a different call than workout
    if ((data.value === 'sleep') ||
    (data.value === 'sleep.light') ||
    (data.value === 'sleep.deep') ||
    (data.value === 'sleep.rem')) {
      data.sampleType = 'HKCategoryTypeIdentifierSleepAnalysis';
      data.amount = 1; // amount or value??
      window.plugins.healthkit.saveQuantitySample(data, onSuccess, onError);
    } else if (data.value === 'sleep.awake') {
      data.sampleType = 'HKCategoryTypeIdentifierSleepAnalysis';
      data.amount = 0; // amount or value??
      window.plugins.healthkit.saveQuantitySample(data, onSuccess, onError);
    } else {
      // some other kind of workout
      data.activityType = data.value;
      if (data.calories) {
        data.energy = data.calories;
        data.energyUnit = 'kcal';
      }
      if (data.distance) {
        data.distance = data.distance;
        data.distanceUnit = 'm';
      }
      window.plugins.healthkit.saveWorkout(data, onSuccess, onError);
    }
  } else if (dataTypes[ data.dataType ]) {
    // generic case
    data.sampleType = dataTypes[ data.dataType ];
    if ((data.dataType === 'distance') && data.cycling) {
      data.sampleType = 'HKQuantityTypeIdentifierDistanceCycling';
    }
    data.amount = data.value;
    if (units[ data.dataType ]) {
      data.unit = units[ data.dataType ];
    }
    window.plugins.healthkit.saveQuantitySample(data, onSuccess, onError);
  } else {
    onError('unknown data type ' + data.dataType);
  }
};

cordova.addConstructor(function () {
  navigator.health = new Health();
  return navigator.health;
});
