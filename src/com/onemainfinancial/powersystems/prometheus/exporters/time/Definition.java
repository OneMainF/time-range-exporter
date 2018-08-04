package com.onemainfinancial.powersystems.prometheus.exporters.time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.Type;


public class Definition{
	public Definition(HashMap<String,Object> props) {
		name=Main.prefix+props.get("name").toString();
		
		//if not added yet, create MetricFamilySamples
		if(!samples.containsKey(name)){
			MetricFamilySamples sample=new MetricFamilySamples(name, Type.GAUGE, props.getOrDefault("help","").toString(), new ArrayList<MetricFamilySamples.Sample>());
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
		
		//if labels are set, add to metric labels
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
	//use LinkedHashMap to retain order
	static LinkedHashMap<String,MetricFamilySamples> samples=new LinkedHashMap<String,MetricFamilySamples>();
	static HashMap<String,ArrayList<String>> metric_labels=new HashMap<String,ArrayList<String>>();
	
	//return a list of all MetricFamilySamlples
	public static List<MetricFamilySamples> getList() {
		List<MetricFamilySamples> list =new ArrayList<MetricFamilySamples>();
		for(String s:samples.keySet()) {
			list.add(samples.get(s));
		}
		return list;
	}
	
	//clear all samples and metrics
	public static void reset() {
		clearSamples();
		samples.clear();
		metric_labels.clear();
	}
	
	//clear previous samples
	public static void clearSamples() {
		for(MetricFamilySamples sample:samples.values()) {
			sample.samples.clear();
		}
	}
	
	//evaluate conditions
	public void evaluate(HashMap<String,Object> bindings) {
		//get labels for this metric
		ArrayList<String> labelNames=metric_labels.get(name);
		ArrayList<String> labelValues=new ArrayList<String>();
		//get label values 
		for(String s:labelNames) {
			labelValues.add(labels.getOrDefault(s, "").toString());
		}
		
		Integer value=0;
		if(!match.equals("any")) {
			value=1;
		}
		
		for(String condition:conditions) {
			Boolean matches= Boolean.valueOf(Main.jinjava.render("{{"+condition+"}}",bindings));
			//if 'any' and matches, set true
			if(match.equals("any")) {
				if(matches) {
					value=1;
					break;
				}
			//if 'none' and matches, set false
			}else if(match.equals("none")) {
				if(matches) {
					value=0;
					break;
				}
		    //if 'all' and not matches, set false
			}else {
				if(!matches) {
					value=0;
					break;
				}
			}	
		}
		
		//add sample
		samples.get(name).samples.add(new MetricFamilySamples.Sample(name,labelNames,labelValues, value));
		
		
	}
	
	
}
