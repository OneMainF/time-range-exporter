package com.onemainfinancial.powersystems.prometheus.exporters.time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import com.ibm.xylem.Logger;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.Type;


public class Definition{
	public Definition(HashMap<String,Object> props) {
		name=Main.prefix+props.get("name").toString();
		if(!samples.containsKey(name)){
			MetricFamilySamples sample=new MetricFamilySamples(name, Type.GAUGE, props.getOrDefault("help","").toString(), new  ArrayList<MetricFamilySamples.Sample>());
			samples.put(name,sample);	
			metric_labels.put(name, new ArrayList<String>());
		}
		
		
		if(props.containsKey("conditions")) {
			if(props.get("conditions") instanceof String) {
				conditions=new ArrayList<String>();
				conditions.add(props.get("conditions").toString());
			}else {
				conditions=(ArrayList<String>) props.get("conditions");
			}
		}
		
		match=props.getOrDefault("match","all").toString().toLowerCase();
		
		if(props.containsKey("labels")) {
			labels=(HashMap<String, Object>) props.get("labels");
			for(String s:labels.keySet()) {
				if(!metric_labels.get(name).contains(s)) {
					metric_labels.get(name).add(s);
				}
			}
		}
	}
	
	ArrayList<String> conditions=new ArrayList<String>();
	String match;
	String name;
	HashMap<String,Object> labels=new HashMap<String,Object>();
	
	static LinkedHashMap<String,MetricFamilySamples> samples=new LinkedHashMap<String,MetricFamilySamples>();
	static HashMap<String,ArrayList<String>> metric_labels=new HashMap<String,ArrayList<String>>();
	
	public static List<MetricFamilySamples> getList() {
		List<MetricFamilySamples> list =new ArrayList<MetricFamilySamples>();
		for(String s:samples.keySet()) {
			list.add(samples.get(s));
		}
		return list;
	}
	public static void reset() {
		clear();
		samples.clear();
		metric_labels.clear();
	}
	public static void clear() {
		for(MetricFamilySamples sample:samples.values()) {
			sample.samples.clear();
		}
	}
	public void evaluate(HashMap<String,Object> bindings) {
		ArrayList<String> labelNames=metric_labels.get(name);
		ArrayList<String> labelValues=new ArrayList<String>();
		for(String s:labelNames) {
			labelValues.add(labels.getOrDefault(s, "").toString());
		}
		Integer value=0;
		if(!match.equals("any")) {
			value=1;
		}
		
		for(String condition:conditions) {
			Boolean matches= Boolean.valueOf(Main.jinjava.render("{{"+condition+"}}",bindings));
			if(match.equals("any")) {
				if(matches) {
					value=1;
					break;
				}
			}else if(match.equals("none")) {
				if(matches) {
					value=0;
					break;
				}
			}else {
				if(!matches) {
					value=0;
					break;
				}
			}	
		}
		samples.get(name).samples.add(new MetricFamilySamples.Sample(name,labelNames,labelValues, value));
		
		
	}
	
	
}
