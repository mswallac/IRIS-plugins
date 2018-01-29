import org.apache.commons.math3.analysis.differentiation.FiniteDifferencesDifferentiator;
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableVectorFunction;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;

public class irisFun implements MultivariateJacobianFunction {
	public int t;
	public double temp;
	public IrisUtils iu;
	
	public irisFun(IrisUtils in1,double temp) {
		iu=in1;
		t=(int)temp;
	}
	
	public Pair<RealVector, RealMatrix> value(RealVector in) {
		double[] din = in.toArray(),y = new double[4];
		y = irisfxn(din[0], din[1], din[2]);
		
		final RealVector ydata = new ArrayRealVector(y);
		final RealMatrix jacobian = getJacobian(din);
		
		Pair<RealVector, RealMatrix> v = new Pair<RealVector, RealMatrix>(ydata, jacobian);
		
		return v;
	}
	
	public double[] irisfxn(double start,double m,double b){
		double sirefract,sirefract2,rsivalue,rvalue,s,filmr,medr;
		double[] result = {0,0,0,0}, im = new double[251], mir = new double[251];
		
		for(int j=0;j<4;j++) {
			int ct=0;
			for(double i=.4;i<.651;i+=0.001){
				sirefract=iu.interpolateSI(i);
				sirefract2=iu.SiRI(i,temp);
				filmr=iu.getFilm(i,temp);
				medr=iu.getMedium(i,temp);
				rsivalue=(iu.fresnel(medr,medr,sirefract,start,i));
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
	
	public RealMatrix getJacobian(double[] in) {
		irisD irisd = new irisD(iu,t,in[1],in[2]);
		irisM irism = new irisM(iu,t,in[0],in[2]);
		irisB irisb = new irisB(iu,t,in[0],in[1]);
		FiniteDifferencesDifferentiator diff = new FiniteDifferencesDifferentiator(5, .25);
		UnivariateDifferentiableVectorFunction irisdd = diff.differentiate(irisd),
				irisdm = diff.differentiate(irism), irisdb = diff.differentiate(irisb);
		double[] dvals = irisdd.value(in[0]),mvals = irisdm.value(in[1]),bvals = irisdb.value(in[2]);
		final RealMatrix jacobian = new Array2DRowRealMatrix(4,3);
		jacobian.setColumn(0, dvals);
		jacobian.setColumn(1, mvals);
		jacobian.setColumn(2, bvals);
		return jacobian;
		
	}
}