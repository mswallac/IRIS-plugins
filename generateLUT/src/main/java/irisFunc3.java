
import org.ddogleg.optimization.functions.FunctionNtoM;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.FastMath;
import ij.IJ;
//CLASS FOR ACCURATE SETTING (Calculates thickness (d))

public class irisFunc3 {
	public int t;
	public double tmp;
	public double[] ydata;
	public IrisUtils iu;
	public boolean set=false;
	
	public irisFunc3(IrisUtils in1,double temp, double[] y) {
		this.iu=in1;
		tmp=temp;
		ydata=y;
	}

	public int getNumOfInputsN() {
		return 3;
	}

	public int getNumOfOutputsM() {
		return 4;
	}

	public double[] process(double start,double m,double b) {
		double sirefract,sirefract2,rsivalue,rvalue,s,filmr,medr,temp=tmp;
		double[] result = {0,0,0,0}, im = new double[477], mir = new double[477];
		for(int j=0;j<4;j++) {
			int ct=0;
			for(double i=.3500001;i<.827;i+=0.001){
				sirefract=iu.interpolateSI(i);
				sirefract2=iu.SiRI(i,temp);
				filmr=iu.getFilm(i,temp);
				medr=iu.getMedium(i,temp);
				rsivalue=-(iu.fresnel(1,1,sirefract,start,i));
				rvalue=(iu.fresnel(medr,filmr,sirefract,start,i));
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
			result[j] = ydata[j] - ((result[j]*m) + b);
		}
		return result;
	}


}
