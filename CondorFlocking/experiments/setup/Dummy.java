public class Dummy {
	public static void main(String[] args) throws InterruptedException {
		boolean sleep = true;
		long time = 1000;
		for(String s : args) {
			if(s.equals("-b")) {
				sleep = false;
			} else {
				try {
					time = Integer.parseInt(args[0]);
				} catch(Throwable t) {}
			}
		}
		if(sleep) {
			System.out.println("Sleep "+time);
			Thread.sleep(time);
		} else {
			System.out.println("Loop "+time);
			long start = System.currentTimeMillis();
			int loops = 0;
			double d = 10;
			while(System.currentTimeMillis() - start < time) {
				for(int i = 0; i < 1000000;i++) {
					// add something so compiler/jit doenst remove it
					d = d + i;
				}
				loops++;
			}
			System.out.println("Looped "+loops+" times");
		}
	}
}