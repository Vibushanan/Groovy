
class Factorial {

	static void main(String[] args){
		
		println(fact(5));
		
		def list1 = [1,2,4,5,6]
		
		list1  = list1.collect() {it * 2};
		println(list1)
		
		
		def getFactorial = {num -> (num <= 1 ? 1 : num * call(num - 1))};

		println(getFactorial(5));	
		
		
		list1.each {num -> if(num%2==0) println(num) }
	}
	
	def static fact(num){
		
		if(num <=1 ){
			return 1;
		}else{
		return (num*fact(num-1));
		}
	}
	
}
