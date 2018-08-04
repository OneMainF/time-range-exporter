Time Range Exporter
=====
Exports current time as a Prometheus metrics.
Additional metrics may be exported by providing a configuration file.

## But....why? 

This may seem like a useless exporter, but the ideal is to use it for alerting.  
I couldn't find an easy way to only alert during a specific time range, so I made a solution.


## Running

To run [download the jar](https://github.com/OneMainF/time_range_exporter/releases/download/1.0/time-range-exporter-1.0.0.jar) and run:

```
java -jar time-range-exporter-1.0.0.jar
```

Metrics will now be accessible at http://localhost:8080/metrics

[jin-java](https://github.com/HubSpot/jinjava) is used for condition evaluation

The following variables are available.

Some metrics are exported by default in camel case format with the prefix name prepended.
For example `time_range_hour`

Name     | Description
---------|------------
system | contains all the values from `System.getProperties()`
environment | contains all the values from `System.getenv()`
now | A [joda-time](https://github.com/JodaOrg/joda-time) DateTime object.  Any of the methods from the object may be accessed for metric values.  For example `now.getDayOfYear()`
time | Current time in format HHmmss.  Exported by default as `time`.
hour | Current hour of day.  Exported by default as `hour`.
minute | Current minute of day.  Exported by default as `minute`.
second | Current second of minute.  Exported by default as `second`.
week | Current week of year.  Exported by default as `week`.
month | Current month of year.  Exported by default as `month`.
year | Current year.  Exported by default as `year`.
weekOfMonth | Current week of the month.  Exported by default as `week_of_month`.
dayOfMonth | Current day of the month.  Exported by default as `day_of_month`.
dayOfWeek | Current day of the week.  Exported by default as `day_of_week`.
dayOfYear | Current day of the year.  Exported by default as `day_of_year`.




## Building

`mvn package` to build.


## Configuration
Configuration is optional and may be used to add additional metrics.

The configuration is in YAML syntax.

May be specified with the `-c` command line option, `time.prometheus.exporter.config` system property, or `TIME_EXPORTER_CONFIG` environment variable

Config file is monitored for updates dynamically.

Example config:

```yaml
port: 8080
prefix: "time_range_"
metrics:
 - name: callcenter_open
   help: help text here
   value: "hour > 9 and hour < 21"
   labels:
     label: value
 - name: year_of_century
   help: Year of the current century
   value: "now.getYearOfCentury()"
```

Name     | Description
---------|------------
port | Port to listen on.  Command line argument will override.  Defaults to `8080`
prefix | Prefix name for metrics.  Defaults to `time_range_`
metrics | A list of metrics to export.  If the same name is specified for multiple metrics, labels will be merged, and the first set of help will be used
name | Name for metric.  Prefix will be prepended
help | Help text for metric.  Defaults to empty
value | Value to use for the metric.  If true is returned, value will be set to 1.  If false is returned, value will be set to 0.  If a valid number is returned, value will be set to that number.
labels | Labels to set for metric

## Authors

* **Cody Moore <cody.moore@omf.com>**


## License

Apache 2.0
