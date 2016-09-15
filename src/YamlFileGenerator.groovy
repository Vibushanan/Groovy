import groovy.json.JsonSlurper
import java.nio.file.Path
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions


def cli = new CliBuilder(usage: 'groovy NiFiDeploy.groovy [options]',
                         header: 'Options:')
cli.with {
  t longOpt: 'template', 'Template URI (override)',
    args:1, argName:'uri', type:String.class
  f longOpt: 'file',
	'Properties File',
	args:1, argName:'name', type:String.class
  n longOpt: 'nifiapi', 'NiFi REST API (override), e.g. http://example.com:9090',
	args:1, argName:'http://host:port', type:String.class
	
  
}

def opts = cli.parse(args)
println(opts.getProperties().entrySet().getAt(0))

opts.arguments().each {
	lt -> println(lt)
}

def propertiesFile;


if (opts.file) {
  propertiesFile = opts.file

} else {
  println "ERROR: Missing a file argument\n"
  cli.usage()
  System.exit(-1)
}




def  templateUri=opts.template;
def nifiuri = opts.nifiapi
def  y = [:];
def  t = new XmlSlurper().parse(templateUri)
def  processorGroupMap = [:];

loadPropertiesMap(propertiesFile,processorGroupMap)
//Create a Data Structure

		y.nifi = [:]

		y.nifi.url= nifiuri;

		y.nifi.clientId='REPLACEME';

		y.nifi.templateUri = templateUri

		y.nifi.templateName = t.name.text()

		y.nifi.gracefullShutDown=Boolean.TRUE;

		y.nifi.undeploy;

		y.undeploy=[:];

		y.undeploy.processGroups=[t.snippet.processGroups.size()];

		y.undeploy.controllerServices=[t.snippet.controllerServices.size()];

		y.undeploy.templates=[t.name.text()]
		
		def controlSerCount=0;
		def cSerName =y.undeploy.controllerServices;

		if (t.snippet.controllerServices.size() > 0) {

			t.snippet.controllerServices.each { xCs ->

				cSerName[controlSerCount]=xCs.name.text();

				y.controllerServices = [:]

				def yC = y.controllerServices

				yC[xCs.name.text()] = [:]
				yC[xCs.name.text()].state = 'ENABLED'

				//Check for property Entry

				def xProps = xCs.properties?.entry

				if (xProps.size() > 0) {

					yC[xCs.name.text()].config = [:]
					xProps.each { xProp ->

						if (xProp.value.size() > 0) {

							yC[xCs.name.text()].config[xProp.key.text()] = xProp.value.text()
						}
					}

				}


			}
		}
		def PGList = y.undeploy.processGroups;
		
		
		
		loadprocessorGroups(PGList,nifiuri)

		y.processGroups = [:]


		if (t.snippet.processors.size() > 0) {

			parseGroup(processorGroupMap,t.snippet,y)

		}


		


		t.snippet.processGroups.each {

			
			parseGroup(processorGroupMap,it,y)
		}


		def yamlOpts = new DumperOptions()
		yamlOpts.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
		yamlOpts.prettyFlow = true

		println("--------------------------Yaml File--------------------------")
		println new Yaml(yamlOpts).dump(y)
		println("--------------------------Yaml File END--------------------------")


	def static parseGroup(processorGroupMap,node,y) {

		def pgName = node?.name.text()

		if (!pgName) {
			pgName = 'root'
		}
		parseProcessors(processorGroupMap,pgName, node,y)
	}



	def static loadprocessorGroups(PGList,nifiuri){		
		
				def count = 0;
				
				
		def data =new JsonSlurper().parseText( new URL(nifiuri+"nifi-api/process-groups/root/process-groups").getText());
		println(data.processGroups.size())
		
		data.processGroups.any  {
			
			processGrp ->
			
			PGList[count]=processGrp.component.name;
			
						count++
			println(processGrp.component.name)
			
		}
	}
	
	def static parseProcessors(processorGroupMap,groupName, node,y) {


		def processors = node.contents.isEmpty() ? node.processors          // root process group
				: node.contents.processors // regular process group


		processors.each { p ->

			p.config.properties?.entry?.each {
				if(processorGroupMap.containsKey(groupName.toString().toLowerCase().trim())){

					def processorsMap = processorGroupMap[groupName.toString().toLowerCase().trim()]
					if(processorsMap.containsKey(p.name.text().toLowerCase().trim())){
						def propertiesMap = processorsMap[p.name.text().toLowerCase().trim()]
						if(propertiesMap.containsKey(it.key.text().toLowerCase().trim())){
							/*println("Succeeded..............   ")
							 println("in if has Value "+it.value.size());*/
							y.processGroups[groupName] = [:]
							y.processGroups[groupName].processors = [:]
							y.processGroups[groupName].processors[p.name.text()] = [:]
							y.processGroups[groupName].processors[p.name.text()].config = [:]
							def c = y.processGroups[groupName].processors[p.name.text()].config
							if (it.value.text() ==~ /[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{12}/) {
								def n = t.snippet.controllerServices.find { cs -> cs.id.text() == it.value.text() }

								/* if(n.isEmpty()){
								 c[it.key.text()] = '\${' + "EMpty"  + "}"
								 }*/
								assert !n.isEmpty() : "Couldn't resolve a Controller Service with ID: ${it.value.text()}"

								c[it.key.text()] = '\${' + n.name.text() + "}"
							}


							else  if (it.value.size() > 0) {

								// println("in else if has Value "+it.value.size());

								c[it.key.text()] = it.value.size() == 0 ? null : it.value.text()
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

	def static loadPropertiesMap(propertiesFile,processorGroupMap){

		new File(propertiesFile).each { line ->
			def record = line.toString()

			def pGroup = record.substring(record.indexOf("processgroups."),record.indexOf(".processors.")).replaceAll("processgroups.", "")
			def processor =  record.substring(record.indexOf(".processors."),record.indexOf(".config.")).replaceAll(".processors.", "")
			def property = record.substring(record.indexOf(".config.")).replaceAll(".config.", "")
			def propertyName=	property.substring(0, property.toString().indexOf("="));
			def propertyValue = property.substring(property.toString().indexOf("=")).replaceAll("=", "")
			
		   println("Group :"+pGroup)
		   println("processor :"+processor)
		   println("propertyName :"+propertyName)
		   println("propertyValue :"+propertyValue)
		   
		   
			//Processor Group map has pGroup
			if(processorGroupMap.containsKey(pGroup)){
				
			  //Check for Processors in PGroup
			if(processorGroupMap[pGroup].containsKey(processor)){
			
				processorGroupMap[pGroup][processor].putAt(propertyName, propertyValue)
				
				processorGroupMap.each {
					lt -> println(lt)
				}
				
			}else{
			//if Processor is not Found 
			def propertyValMap= [:];
			propertyValMap.put(propertyName, propertyValue);
			processorGroupMap[pGroup].putAt(processor,propertyValMap)
			processorGroupMap.each {
				lt -> println(lt)
			}
			}}
			else{
			//If the entire Group is not there
				
			def propertyValMap= [:];
			def processorMap=[:];
			propertyValMap.put(propertyName, propertyValue);
			processorMap.put(processor, propertyValMap)
			processorGroupMap.put(pGroup, processorMap)
			processorGroupMap.each {
				lt -> println(lt)
			}
			
			}
		
		
	
		}
		
		println("-----------Property Map Loaded--------------")
		println(processorGroupMap.dump()
			)}



