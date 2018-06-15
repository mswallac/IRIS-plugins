import org.ddogleg.optimization.functions.FunctionNtoM;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.FastMath;
import ij.IJ;
//CLASS FOR ACCURATE SETTING (Calculates thickness (d))

public class irisFunc implements FunctionNtoM {
	public int t;
	public double tmp;
	public double[] ydata;
	public IrisUtils iu;
	public boolean set=false;
	
	public irisFunc(IrisUtils in1,double temp, double[] y) {
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

	public void process(double[] in, double[] out) {
		double sirefract,sirefract2,rsivalue,rvalue,s,filmr,medr,start=in[0],m=in[1],b=in[2],temp=tmp;
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
			if(start>0.120)
				out[j] = ydata[j] - ((result[j]*m) + b);
		}
		return;
	}


}
