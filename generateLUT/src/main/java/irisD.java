
import org.apache.commons.math3.analysis.UnivariateVectorFunction;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.FastMath;

public class irisD implements UnivariateVectorFunction {
	public double b,m;
	public int t;
	public IrisUtils iu;
	
	public irisD(IrisUtils in1,double temp,double inm, double inb) {
		iu=in1;
		t=(int)temp;
		b=inb;
		m=inm;
	}

	public double[] value(double d) {
		// TODO Auto-generated method stub
		double[] val = irisfxn(d,m,b);
		return val;
	}
	
	public double[] irisfxn(double start,double m,double b){
		double sirefract,sirefract2,rsivalue,rvalue,s,filmr,medr;
		double[] result = {0,0,0,0}, im = new double[251], mir = new double[251];
		
		for(int j=0;j<4;j++) {
			int ct=0;
			for(double i=.4;i<.651;i+=0.001){
				sirefract=iu.interpolateSI(i);
				sirefract2=iu.SiRI(i,t);
				filmr=iu.getFilm(i,t);
				medr=iu.getMedium(i,t);
				rsivalue=(iu.fresnel(1,1,sirefract,start,i));
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
			result[j] = ((result[j]*m) + b);
		}
		
		return (result);
		
	}
}
