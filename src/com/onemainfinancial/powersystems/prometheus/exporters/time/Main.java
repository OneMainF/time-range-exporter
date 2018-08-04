package com.onemainfinancial.powersystems.prometheus.exporters.time;




import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;


import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.joda.time.DateTime;
import org.joda.time.Weeks;
import org.yaml.snakeyaml.Yaml;

import com.hubspot.jinjava.Jinjava;


import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.Type;
import io.prometheus.client.exporter.MetricsServlet;





public class Main extends Collector {
	public Main(HashMap<String,Object> config){
		update(config);
	}
	public static void update(HashMap<String,Object> config) {
		Definition.clear();
		prefix=config.getOrDefault("prefix","time_range_").toString();
		@SuppressWarnings({ "unchecked", "rawtypes" })
		ArrayList<HashMap<String,Object>> metrics=(ArrayList) config.getOrDefault("metrics",new ArrayList<Object>());
		for(HashMap<String,Object> metric:metrics) {
		    definitions.add(new Definition(metric));	
		}
		
		
	}
	static Server server=null;
	static Integer port=8080;
	static Yaml yaml=new Yaml();
	static Logger logger=Logger.getLogger(Main.class);
	static Jinjava jinjava=new Jinjava();
	static HashMap<String,Object> bindings=new HashMap<String,Object>();
	static String prefix;
	static ArrayList<Definition> definitions=new ArrayList<Definition>();
	
	static {
		bindings.put("system", System.getProperties());
		bindings.put("environment", System.getenv());
	}
	
	public static HashMap<String,Object> readConfig(String filename){
		Path path=Paths.get(filename);
		HashMap<String,Object> returnData=new HashMap<String,Object>();
		logger.info("Loading config file "+path.toString());
		try {
			byte[] encoded = Files.readAllBytes(path);
			String data=new String(encoded, Charset.defaultCharset());
			//data=jinjava.render(data, bindings);
        	Object map=yaml.load(data);
        	if(map instanceof ArrayList) {
        		returnData.put("metrics", map);
        	}else {
        		returnData=(HashMap<String, Object>) map;
        	}
    	}catch(Exception e) {
    		logger.error("Could not load config",e);
    	}
		return returnData;
	}
	

   
	
	public static void main(String[] args) throws Exception{
	    CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        
        options.addOption(Option
        		.builder("h")
                .longOpt("help")
                .desc("Print this again")
                .build());
        options.addOption(Option
        		.builder("p") 
                .longOpt("port")
                .desc("Port for embedded jetty to listen on.  May also be specified in config file.")
                .hasArg()
                .argName("port")
                .build());
        options.addOption(Option
        		.builder("c")
                .longOpt("config")
                .desc("Config file.  Overwrites the time.prometheus.exporter.config system property.")
                .hasArg()
                .argName("file")
                .build());        
        try{
	        CommandLine line = parser.parse(options, args);
	        if(line.hasOption("h")){
	        	  new HelpFormatter().printHelp("Time Range Prometheus Exporter", options);
	              System.exit(0);
	        }
	        
	        if(line.hasOption("c")){
	        	System.setProperty("time.prometheus.exporter.config",line.getOptionValue("config"));
	        }

	        
	        
	        Properties log_props=new Properties();
			log_props.put("log4j.rootLogger", "INFO, stdout");
			log_props.put("log4j.appender.stdout","org.apache.log4j.ConsoleAppender");
			log_props.put("log4j.appender.stdout.Target","System.out");
			log_props.put("log4j.appender.stdout.layout","org.apache.log4j.PatternLayout");
			log_props.put("log4j.appender.stdout.layout.ConversionPattern","[%d{yyyy-MM-dd HH:mm:ss}] [%-5p] [%-20c{1}] %m%n");
			PropertyConfigurator.configure(log_props);
			
	        
	    	String file;
	        if(System.getProperty("time.prometheus.exporter.config") !=null) {
	        	file=System.getProperty("time.prometheus.exporter.config");	
	        }else if(System.getenv().containsKey("TIME_EXPORTER_CONFIG")) {
	        	file=System.getenv("TIME_EXPORTER_CONFIG");
	        }else {
	        	file="/data/time-prometheus-exporter.yaml";
	        }
	        
	        
	        HashMap<String,Object> config=new HashMap<String,Object>();
	        if(line.hasOption("port")) {
		        try{
		            port=Integer.parseInt(line.getOptionValue("port"));
		        }catch(Exception e){
		        	throw new Exception(line.getOptionValue("port")+" is not a valid port");
		        }
		        
	        }
	        final File configFile=new File(file);
	        if(configFile.isFile()) {
	        	config=readConfig(configFile.getAbsolutePath());
	        	new Thread(){
		    	    	long lastMod=0;
		    	    	public void run(){
		    	    		lastMod=configFile.lastModified();
		    	    		while(true){
		    	    		   try {
		    					  Thread.sleep(5000);
		    				   } catch (InterruptedException e) {
		    					  break;
		    				   }
		    	    		   if(configFile.lastModified()!=lastMod){
		    	    			   lastMod=configFile.lastModified();
		    	    			   try {
		    	    				 Main.update(readConfig(configFile.getAbsolutePath()));
		    					   } catch (Exception e) {
		    						 e.printStackTrace();
		    					  }
		    	    		   }
		    	    		}	    		
		    	    	}
		    	    }.start();
		        
	        }else {
	        	logger.error("Could file does not exist or is not accessible");
	        	System.exit(1);
	        }
	        
	        if(config.containsKey("port")) {
	        	port=Integer.valueOf(config.get("port").toString());
	        }
	       	        
	        new Main(config).register();
	        startServer();
        }catch(Exception e){
        	System.out.println("Error: "+e.getMessage());
        	System.out.println("");
        }
        
	}
	
	public static void startServer() throws Exception {
		 logger.info("Starting Jetty on port "+port);
		 server = new Server(port);
	     ServletContextHandler context = new ServletContextHandler();
	     context.setContextPath("/");
	     server.setHandler(context);
	     context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
	     server.start();
	     server.join();
	}




	@Override
	public List<MetricFamilySamples> collect() {
		Definition.clear();

		DateTime dt=DateTime.now();

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(dt.toDate());
		int weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH);
		    
		
		HashMap<String,Object> dateBindings=new HashMap<String,Object>();
		dateBindings.put("now", dt);
		dateBindings.put("hour", dt.getHourOfDay());
		dateBindings.put("minute", dt.getMinuteOfDay());
		dateBindings.put("week", dt.getWeekOfWeekyear());
		dateBindings.put("weekOfMonth",weekOfMonth);
		dateBindings.put("dayOfMonth", dt.getDayOfMonth());
		dateBindings.put("dayOfWeek", dt.getDayOfWeek());
		dateBindings.put("dayOfYear", dt.getDayOfYear());
		dateBindings.put("year", dt.getYear());
		dateBindings.put("month", dt.getMonthOfYear());
		logger.info("Evaluating time "+dateBindings);
		bindings.putAll(dateBindings);
		
		
		for(Definition definition:definitions) {
		   definition.evaluate(bindings);   
		}
		
		 
		return Definition.getList();
	}
	
}
