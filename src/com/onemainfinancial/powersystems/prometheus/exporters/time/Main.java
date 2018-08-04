package com.onemainfinancial.powersystems.prometheus.exporters.time;




import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.yaml.snakeyaml.Yaml;

import com.hubspot.jinjava.Jinjava;

import io.prometheus.client.Collector;
import io.prometheus.client.exporter.MetricsServlet;





public class Main extends Collector {	
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
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static HashMap<String,Object> readConfig(String filename){
		Path path=Paths.get(filename);
		HashMap<String,Object> returnData=new HashMap<String,Object>();
		logger.info("Loading config file "+path.toString());
		try {
			byte[] encoded = Files.readAllBytes(path);
			String data=new String(encoded, Charset.defaultCharset());
			//evaluate any templating
			data=jinjava.render(data, bindings);
			//load to map
        	Object map=yaml.load(data);
    		//if an array list, convert to map
        	if(map instanceof ArrayList) {
        		returnData.put("metrics", map);
        	}else {
        		returnData=(HashMap<String, Object>) map;
        	}
    	}catch(Exception e) {
    		logger.error("Could not load config",e);
    	}
		
		//Reset definitions in case of update
		Definition.reset();
		prefix=returnData.getOrDefault("prefix","time_range_").toString();
		ArrayList<HashMap<String,Object>> metrics=(ArrayList) returnData.getOrDefault("metrics",new ArrayList<Object>());
		//create definitions
		for(HashMap<String,Object> metric:metrics) {
		    definitions.add(new Definition(metric));	
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

	        
	        // set console logger
	        // may add option for log file later
	        Properties log_props=new Properties();
			log_props.put("log4j.rootLogger", "INFO, stdout");
			log_props.put("log4j.appender.stdout","org.apache.log4j.ConsoleAppender");
			log_props.put("log4j.appender.stdout.Target","System.out");
			log_props.put("log4j.appender.stdout.layout","org.apache.log4j.PatternLayout");
			log_props.put("log4j.appender.stdout.layout.ConversionPattern","[%d{yyyy-MM-dd HH:mm:ss}] [%-5p] [%-20c{1}] %m%n");
			PropertyConfigurator.configure(log_props);
			
	        //find config file
	    	String file;
	        if(System.getProperty("time.prometheus.exporter.config") !=null) {
	        	file=System.getProperty("time.prometheus.exporter.config");	
	        }else if(System.getenv().containsKey("TIME_EXPORTER_CONFIG")) {
	        	file=System.getenv("TIME_EXPORTER_CONFIG");
	        }else {
	        	file="/data/time-prometheus-exporter.yaml";
	        }
	        
	        
	        
	   
	        final File configFile=new File(file);
	  
	        if(!configFile.isFile()) {
	        	logger.error("Could file does not exist or is not accessible");
	        	System.exit(1);
	        }
	        
	        HashMap<String,Object> config = readConfig(configFile.getAbsolutePath());
	        
	        
	        //start file monitor thread
	        new Thread(){
		    	long lastMod=0;
		    	public void run(){
		    		lastMod=configFile.lastModified();
		    	    while(true){
		    	    	//check for updates to file
		    	       try {
		    			  Thread.sleep(5000);
		    		   } catch (InterruptedException e) {
		    			  break;
		    		   }
		    	       if(configFile.lastModified()!=lastMod){
		    	    	   lastMod=configFile.lastModified();
		    	    	   try {
		    	    		 readConfig(configFile.getAbsolutePath());
		    			   } catch (Exception e) {
		    				 e.printStackTrace();
		    		   }
		    	     }
		    	   }	    		
		    	}
		    }.start();
		        
	        
	        
	        //read port argument
	        if(line.hasOption("port")) {
		        try{
		            port=Integer.parseInt(line.getOptionValue("port"));
		        }catch(Exception e){
		        	throw new Exception(line.getOptionValue("port")+" is not a valid port");
		        }
		        
	        }else if(config.containsKey("port")) {
	        	//read config file port
	        	port=Integer.valueOf(config.get("port").toString());
	        }
	       	//register collector and start   
	        new Main().register();
 		    logger.info("Starting Jetty on port "+port);
			Server server = new Server(port);
		    ServletContextHandler context = new ServletContextHandler();
		    context.setContextPath("/");
		    server.setHandler(context);
		    context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
		    server.start();
		    server.join();
		     
        }catch(Exception e){
        	System.out.println("Error: "+e.getMessage());
        	System.out.println("");
        }
        
	}


	@Override
	public List<MetricFamilySamples> collect() {
		//clear last samples
		Definition.clearSamples();

		DateTime dt=DateTime.now();
		
		//week of month is not available in JodaTime
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(dt.toDate());
		int weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH);
		    
		bindings.put("now", dt);
		bindings.put("hour", dt.getHourOfDay());
		bindings.put("minute", dt.getMinuteOfDay());
		bindings.put("week", dt.getWeekOfWeekyear());
		bindings.put("weekOfMonth",weekOfMonth);
		bindings.put("dayOfMonth", dt.getDayOfMonth());
		bindings.put("dayOfWeek", dt.getDayOfWeek());
		bindings.put("dayOfYear", dt.getDayOfYear());
		bindings.put("year", dt.getYear());
		bindings.put("month", dt.getMonthOfYear());
		
		//evaluate metrics
		for(Definition definition:definitions) {
		   definition.evaluate(bindings);   
		}
		
		//return metric list
		return Definition.getList();
	}
	
}
