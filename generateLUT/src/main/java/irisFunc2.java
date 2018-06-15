import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.FastMath;
import org.ddogleg.optimization.functions.FunctionNtoM;
//CLASS FOR RELATIVE SETTING (does not calculate thickness)

public class irisFunc2 implements FunctionNtoM {
	public int t;
	public double thickness;
	public IrisUtils iu;
	public double[] ydata;
	
	public irisFunc2(IrisUtils in1,double temp,double d,double[] y) {
		iu=in1;
		t=(int)temp;
		thickness=d;
		ydata=y;
	}

	public int getNumOfInputsN() {
		return 2;
	}

	public int getNumOfOutputsM() {
		return 4;
	}

	public void process(double[] in, double[] out) {
		double sirefract,sirefract2,rsivalue,rvalue,s,filmr,medr,start=thickness,m=in[0],b=in[1],temp=t;
		double[] result = {0,0,0,0}, im = new double[477], mir = new double[477];
		for(int j=0;j<4;j++) {
			int ct=0;
			for(double i=.3500001;i<.827;i+=0.001){
				sirefract=iu.interpolateSI(i);
				sirefract2=iu.SiRI(i,temp);
				filmr=iu.getFilm(i,temp);
				medr=iu.getMedium(i,temp);
				rsivalue=-(iu.fresnel(1,1,sirefract,start,i));
				rvalue=(iu.fresnel(medr,filmr,sirefract2,start,i));
				s=(iu.interpolateLED(j,i));
				s=(FastMath.sqrt(s));
				if(s>=0) {
					mir[ct]=FastMath.pow(((s)*rsivalue),2);
					im[ct]=FastMath.pow(((s)*rvalue),2);
				}else {
					mir[ct]=0;
					im[ct]=0;
				}
				ct++;
			}
			result[j] = ((StatUtils.sum(im))/(StatUtils.sum(mir)));
			out[j] = ydata[j] - ((result[j]*m) + b);
		}
		return;
	}

}
