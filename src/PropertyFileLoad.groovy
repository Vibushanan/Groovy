
class PropertyFileLoad {

	static main(args) {
		
		
		def processorGroupMap = [:];
		def processorMap= [:];
		
		
		
		new File("D:\\property_file.txt").each {
			line -> 
			def record = line.toString()
			
			def pGroup = record.substring(record.indexOf("processgroups."),record.indexOf(".processors.")).replaceAll("processgroups.", "")
			def processor =  record.substring(record.indexOf(".processors."),record.indexOf(".config.")).replaceAll(".processors.", "")
			def property = record.substring(record.indexOf(".config.")).replaceAll(".config.", "")
			def propertyName=	property.substring(0, property.toString().indexOf("="));
			def propertyValue = property.substring(property.toString().indexOf("=")).replaceAll("=", "")
			
			
			if(processorMap.containsKey(processor)){
				processorMap[processor].putAt(propertyName, propertyValue)
			}else{
			
				def propertyValMap= [:];	
				propertyValMap.put(propertyName, propertyValue);
				processorMap.put(processor, propertyValMap)
				println(processorMap)
				
			}
			
			
			if(processorGroupMap.containsKey(pGroup)){
				
				processorGroupMap[pGroup].putAt(pGroup, processorMap)
			}else{
			processorGroupMap.put(pGroup, processorMap)
			
			}
			
			
		}
	
	}

}
