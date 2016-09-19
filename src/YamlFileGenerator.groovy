import groovy.json.JsonSlurper
import groovyx.net.http.RESTClient
import java.nio.file.Path
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions


def cli = new CliBuilder(usage: 'groovy NiFiDeploy.groovy [options]',
header: 'Options:')


/*
 * 
 @Grab(group='org.codehaus.groovy.modules.http-builder',
 module='http-builder',
 version='0.7.1')
 @Grab(group='org.yaml',
 module='snakeyaml',
 version='1.17')
 @Grab(group='org.apache.httpcomponents',
 module='httpmime',
 version='4.2.1')
 */

//...............Run Time Parameters.............
cli.with {
	t longOpt: 'template', 'Template URI (override)',
	args:1, argName:'uri', type:String.class
	f longOpt: 'file',
	'Properties File',
	args:1, argName:'name', type:String.class
	n longOpt: 'nifiapi', 'NiFi REST API (override), e.g. http://example.com:9090/nifi-api',
	args:1, argName:'http://host:port', type:String.class
	f1 longOpt: 'fileout',
	'Properties File',
	args:1, argName:'name', type:String.class
	cn longOpt: 'clusteropts',
	'cluster opts',
	args:1, argName:'name', type:String.class
	
}

def opts = cli.parse(args)

def propertiesFile;

def outputFile;


//..................input Check........................
if (opts.file) {
	propertiesFile = opts.file
} else {
	println "ERROR: Missing a file argument\n"
	cli.usage()
	System.exit(-1)
}

if (opts.fileout) {
	outputFile = opts.fileout
} else {
	println "ERROR: Missing a file argument\n"
	cli.usage()
	System.exit(-1)
}




def nifiapiurl = new RESTClient(opts.nifiapi)
def  templateUri=opts.template;
def nifiuri = opts.nifiapi
def  y = [:];
def  t = new XmlSlurper().parse(templateUri)
def  processorGroupMap = [:];
def controllerServicesMap=[:];
def node = opts.clusteropts.toUpperCase()


loadPropertiesMap(propertiesFile,processorGroupMap,controllerServicesMap)

//Create a Data Structure

y.nifi = [:]

y.nifi.url= nifiuri;

y.nifi.clientId='REPLACEME';

y.nifi.templateUri = templateUri

y.nifi.templateName = t.name.text()

y.nifi.gracefullShutDown='True';


y.nifi.undeploy=[:];

y.nifi.undeploy.processGroups=[t.snippet.processGroups.size()];

y.nifi.undeploy.controllerServices=[t.snippet.controllerServices.size()];

y.nifi.undeploy.templates=[t.name.text()]

def controlSerCount=0;

def cSerName =y.nifi.undeploy.controllerServices;

loadControlServices(cSerName,nifiapiurl,node)
y.controllerServices = [:]

def yC = y.controllerServices

if (t.snippet.controllerServices.size() > 0) {

	println("controllerServicesMap : "+controllerServicesMap)

	t.snippet.controllerServices.each { xCs ->

		println("xCs  :"+xCs.name.text())

		yC[xCs.name.text()] = [:]
		yC[xCs.name.text()].state = 'ENABLED'
		
		def xProps = xCs.properties?.entry

		if (xProps.size() > 0) {
			
			println("xProps  :  "+xProps)
			yC[xCs.name.text()].config = [:]
			
			xProps.each { xProp ->

				println(xCs.name.text())

				
				if(controllerServicesMap.containsKey(xCs.name.text().toLowerCase().trim())){


					println("Control Service Key is there ")


					if(controllerServicesMap[xCs.name.text().toLowerCase().trim()].containsKey(xProp.key.text().toLowerCase().trim())){

						println("Config Present")
						yC[xCs.name.text()].config[xProp.key.text()] = xProp.value.text()

					
						
					}

				}





				println("xProp.key.text() : "+xProp.key.text()+"  xProp.value.text() : "+xProp.value.text())


			}

		}


	}
}

println("Y--->   "+y.controllerServices)
def PGList = y.nifi.undeploy.processGroups;



loadprocessorGroups(PGList,nifiapiurl)


y.processGroups = [:]


if (t.snippet.processors.size() > 0) {

	parseGroup(processorGroupMap,t.snippet,y)

}





t.snippet.processGroups.each {


	parseGroup(processorGroupMap,it,y)
}

def yamlOpts = new DumperOptions()
yamlOpts.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK


//println prettyPrint.print(y)

File file = new File("D://willaian.yml")

new Yaml(yamlOpts).dump(y,  new FileWriter(outputFile))

println("Sucessfully Done...")

def static parseGroup(processorGroupMap,node,y) {

	def pgName = node?.name.text()

	if (!pgName) {
		pgName = 'root'
	}
	parseProcessors(processorGroupMap,pgName, node,y)
}


def static loadControlServices(cSerName,nifiapiurl,node){
	
	def resp = nifiapiurl.get(path: "controller/controller-services/"+node);
	assert resp.status == 200
	def count = 0;
	resp.data.controllerServices.each { pGs ->
		cSerName[count]=pGs.name;
		count++
	}
}

def static loadprocessorGroups(PGList,nifiapiurl){

	def resp = nifiapiurl.get(path: "controller/process-groups/root/process-group-references");
	assert resp.status == 200

	def count = 0;
	resp.data.processGroups.each { pGs ->
		PGList[count]=pGs.name;
		count++
	}

}

def static parseProcessors(processorGroupMap,groupName, node,y) {


	def processors = node.contents.isEmpty() ? node.processors          // root process group
			: node.contents.processors // regular process group


	processors.each { p ->
		def ProcessName= p.name.text()
		p.config.properties?.entry?.each {

			//println("Process Group :"+groupName+"  |  Process :"+p.name.text()+"  | Propert :"+it.key.text());
			if(processorGroupMap.containsKey(groupName.toString().toLowerCase().trim())){

				def processorsMap = processorGroupMap[groupName.toString().toLowerCase().trim()]



				if(processorsMap.containsKey(p.name.text().toLowerCase().trim())){

					def propertiesMap = processorsMap[p.name.text().toLowerCase().trim()]




					if(propertiesMap.containsKey(it.key.text().toLowerCase().trim())){

						if(!y.processGroups.containsKey(groupName)){
							//println("Group Not Present   :"+y.processGroups)
							y.processGroups[groupName] = [:]
							y.processGroups[groupName].processors = [:]
							y.processGroups[groupName].processors [p.name.text()] = [:]
							y.processGroups[groupName].processors [p.name.text()].config = [:]
						}else{


							if(y.processGroups[groupName].processors.containsKey(ProcessName)){


							}else{
								y.processGroups[groupName].processors [p.name.text()] = [:]
								y.processGroups[groupName].processors [p.name.text()].config = [:]

							}

						}


						if (it.value.text() ==~ /[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{12}/) {
							def n = t.snippet.controllerServices.find { cs -> cs.id.text() == it.value.text() }


							assert !n.isEmpty() : "Couldn't resolve a Controller Service with ID: ${it.value.text()}"

							y.processGroups[groupName].processors[p.name.text()].config[it.key.text()] = '\${' + n.name.text() + "}"
						}
						else  if (it.value.size() > 0) {



							y.processGroups[groupName].processors[p.name.text()].config[it.key.text()] = it.value.size() == 0 ? "No Value Found" : it.value.text()

						}else{
						def repl='REPLACEME'
							y.processGroups[groupName].processors[p.name.text()].config[it.key.text()]=String.valueOf('REPLACEME')

						}

					}else{
						/*println("Not in Property  ")
						 println("Failed  Group :"+groupName.toString().toLowerCase().trim()+" Processor  :"+p.name.text()
						 +"   Property   :"+it.key.text());*/


					}

				}else{

					/*println("Not in processors  ")
					 println("Failed  Group :"+groupName.toString().toLowerCase().trim()+" Processor  :"+p.name.text()
					 +"   Property   :"+it.key.text());*/
				}


			}else{
				/*println("Not in Group  ")
				 println("Failed  Group :"+groupName.toString().toLowerCase().trim()+" Processor  :"+p.name.text()
				 +"   Property   :"+it.key.text());*/
			}


			// check if it's a UUID and try lookup the CS to get the name
			//println(c)



		}
	}
}

def static loadPropertiesMap(propertiesFile,processorGroupMap,controllerServicesMap){

	new File(propertiesFile).each { line ->
		def record = line.toString()
//println(record)

if(record.contains("processgroups")&&record.contains("processors")&&record.contains("config")){
	
		def pGroup = record.substring(record.indexOf("processgroups."),record.indexOf(".processors.")).replaceAll("processgroups.", "")
		def processor =  record.substring(record.indexOf(".processors."),record.indexOf(".config.")).replaceAll(".processors.", "")
		def property = record.substring(record.indexOf(".config.")).replaceAll(".config.", "")
		def propertyName=	property.substring(0, property.toString().indexOf("="));
		def propertyValue = property.substring(property.toString().indexOf("=")).replaceAll("=", "")


		if(processorGroupMap.containsKey(pGroup)){


			if(processorGroupMap[pGroup].containsKey(processor)){

				processorGroupMap[pGroup][processor].putAt(propertyName, propertyValue)


			}else{
				//if Processor is not Found
				def propertyValMap= [:];
				propertyValMap.put(propertyName, propertyValue);
				processorGroupMap[pGroup].putAt(processor,propertyValMap)

			}}
		else{
			//If the entire Group is not there

			def propertyValMap= [:];
			def processorMap=[:];
			propertyValMap.put(propertyName, propertyValue);
			processorMap.put(processor, propertyValMap)
			processorGroupMap.put(pGroup, processorMap)


		}



	}else if(record.contains("controllerServices")&&record.contains("config")){
	
	//println("Load in Control Services "+record)
	
	def cServices=record.substring(record.indexOf("controllerServices."),record.indexOf(".config.")).replaceAll("controllerServices.", "")
	def config = record.substring(record.indexOf(".config.")).replaceAll(".config.", "")
	def configName=	config.substring(0, config.toString().indexOf("="));
	def configValue = config.substring(config.toString().indexOf("=")).replaceAll("=", "")

	//println("Control Service : "+cServices+"  ConfigName  :  "+configName+"  ConfigValue  :  "+configValue)
	
	def configValMap= [:];
	configValMap.putAt(configName,configValue)
	if(controllerServicesMap.containsKey(cServices)){
	
		
		
		controllerServicesMap[cServices].putAt(configName, configValue)
		
		
	}else{
	
	controllerServicesMap.put(cServices,configValMap)
		
	}
	
	//println(controllerServicesMap)
	
	}else{
	println("Lines Skippeed"+line)
	}
	/*	println("MAP")
	 for(e in processorGroupMap){
	 def l = e.value;
	 //print "group = ${e.key}"
	 for(y in l){
	 //println("group = ${e.key} process =  ${y.key}, value = ${y.value}")
	 def rec = y.value
	 for(aRec in rec){
	 println("group = ${e.key} process =  ${y.key}, config = ${aRec.key}, value =${aRec.value}")
	 }
	 }
	 }*/

}

}

