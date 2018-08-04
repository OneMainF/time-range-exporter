Time Range Exporter
=====

Simple Prometheus exporter to set a metric to either 0 for false or 1 for true depending on time conditions


## Running

To run as a jetty server [download the jar](https://evdcigl.corp.fin/dcipowersystems/wasnd-prometheus-exporter/raw/master/releases/wasnd-prometheus-exporter-1.0.0.war) and run:

```
java -jar time-prometheus-exporter-1.0.0.war --port 8080 --config config.yml
```

Metrics will now be accessible at http://localhost:8080/metrics

Jinjava is used for condition evaluation
The following variables are available
`system`,`environment`,`now`,`week`, `dayOfWeek`, `hour`, `minute`, `month`, `dayOfMonth`, `dayOfYear`, `year`, and `weekOfMonth`

`system` contains all the values from `System.getProperties()`
`environment` contains all the values from `System.getenv()`
`now` is a JodaTime DateTime object.  Any of the methods from the object may be accessed in the conditions.  For example `now.getDayOfYear()`



## Building

`mvn package` to build.


## Configuration
The configuration is in YAML syntax.

May be specified with the `-c` command line option, `time.prometheus.exporter.config` system property, or `TIME_EXPORTER_CONFIG` environment variable

Config file is monitored for updates dynamically.

Example config:

```yaml
---
port: 8080
prefix: "time_range_"
metrics:
 - name: callcenter
   match: any
   help: help text here
   conditions: "hour > 9 and hour < 21"
   labels:
     label: value
 - name: intranet
   match: all
   help: help text here
   conditions:
     - "hour > 7 and hour < 13 and dayOfWeek<6"
     - "dayOfWeek < 6"
 - name: some_other_metric
   match: none
   help: help text here
   conditions: hour > 6
```

Name     | Description
---------|------------
port | Port to listen on.  Command line argument will override
prefix | Prefix name for metrics.  Defaults to `time_range_`
metrics | A list of metrics to export.  If the same name is specified for multiple metrics, labels will be merged, and the first set of help will be used
name | Name for metric.  Prefix will be prepended
help | Help text for metric.  Defaults to empty
match | How to handle condition matching. `all` - all conditions must match, `any` - any condition must match, `none` - all conditions must NOT match.  Defaults to `all`
conditions | Either a single or list of conditions.  JinJava will be used to evaluate.
labels | Labels to set for metric

## Authors

* **Cody Moore <cody.moore@omf.com>**


## License

Apache 2.0
