
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.analysis.differentiation.FiniteDifferencesDifferentiator;
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableVectorFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
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
//CLASS FOR ACCURATE SETTING (Calculates thickness (d))
public class irisFun implements MultivariateJacobianFunction {
	public int t;
	public double tmp;
	public IrisUtils iu;
	public boolean set=false;
	public PolynomialFunction[] diffd = new PolynomialFunction[4], diffm = new PolynomialFunction[4],diffb = new PolynomialFunction[4];
	public final WeightedObservedPoints mdata = new WeightedObservedPoints(),bdata = new WeightedObservedPoints(),ddata = new WeightedObservedPoints();
	
	public irisFun(IrisUtils in1,double temp) {
		this.iu=in1;
		tmp=temp;
	}
	
	//get jacobian for use with optimization
	private void setDiff(double[] in) {
		PolynomialCurveFitter pcf = PolynomialCurveFitter.create(0).withStartPoint(new double[] {10e5,-8e6,3e5,-5e4});
		pcf.withMaxIterations(1000);
		for(int j=0;j<4;j++) {
			double[] y = new double[4];
			for(double d=.08;d<=0.22;d+=0.00056) {
				y = irisfxn(d,in[1],in[2]);
				ddata.add(d, y[j]);
			}
			PolynomialFunction df = new PolynomialFunction(pcf.fit(ddata.toList()));
			diffd[j] = df.polynomialDerivative();
		}
		
	}
	
	//function which returns value for use in optimization
	public Pair<RealVector, RealMatrix> value(RealVector in) {
		double[] din = in.toArray(),y = new double[4];
		y = irisfxn(din[0], din[1], din[2]);
		
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
				sirefract2=iu.SiRI(i,tmp);
				filmr=iu.getFilm(i,tmp);
				medr=iu.getMedium(i,tmp);
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
	
	//use setDiff data to create jacobian matrix for return
	public RealMatrix getJacobian(double[] in) {
		if(!set) {
			setDiff(in);
			set=true;
		}
		double[] dvals = new double[4];
		for(int i=0;i<4;i++) {
			dvals[i]=(diffd[i]).value(in[0]);
		}
    	IJ.log("D: "+dvals[0]+" "+dvals[1]+" "+dvals[2]+" "+dvals[3]);
		final RealMatrix jacobian = new Array2DRowRealMatrix(4,3);
		jacobian.setColumn(0, dvals);
		jacobian.setColumn(1, irisfxn(in[0],in[1],in[2]));
		jacobian.setColumn(2, new double[] {1,1,1,1});
		return jacobian;
		
	}
}