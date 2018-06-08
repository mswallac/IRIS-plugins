
import org.apache.commons.math3.analysis.differentiation.FiniteDifferencesDifferentiator;
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableVectorFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;

import ij.IJ;
// CLASS FOR RELATIVE SETTING (Does not calculate thickness (d))
public class irisFun2 implements MultivariateJacobianFunction {
	public int t;
	public double thickness;
	public IrisUtils iu;
	public PolynomialFunction[] diffm = new PolynomialFunction[4],diffb = new PolynomialFunction[4];
	public final WeightedObservedPoints mdata = new WeightedObservedPoints(),bdata = new WeightedObservedPoints();
	
	
	public irisFun2(IrisUtils in1,double temp,double d) {
		iu=in1;
		t=(int)temp;
		thickness=d;
	}
	

	//function which returns value for use in optimization
	public Pair<RealVector, RealMatrix> value(RealVector in) {
		double[] din = in.toArray(),y = new double[4];
		y = irisfxn(thickness, din[0], din[1]);
		
		final RealVector ydata = new ArrayRealVector(y);
		final RealMatrix jacobian = getJacobian(din);
		
		Pair<RealVector, RealMatrix> v = new Pair<RealVector, RealMatrix>(ydata, jacobian);
		
		return v;
	}

	//specialized version of iris function for use with fitter
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

	//get jacobian for use with optimization
	public RealMatrix getJacobian(double[] in) {
		final RealMatrix jacobian = new Array2DRowRealMatrix(4,2);
		jacobian.setColumn(0, irisfxn(thickness,in[0],in[1]));
		jacobian.setColumn(1, new double[] {1,1,1,1});
		return jacobian;
		
	}
}