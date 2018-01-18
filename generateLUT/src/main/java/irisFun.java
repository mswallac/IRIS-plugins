import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;

import ij.IJ;
import ij.text.TextPanel;
import ij.text.TextWindow;

public class irisFun implements MultivariateJacobianFunction {
	public int s1=2501,s2=129,t;
	public double[][] c1 = new double[2501][2],c2 = new double[2501][2],c3 = new double[2501][2],c4 = new double[2501][2],
			risi = new double[129][2],risio2 = new double[101][2],ripmma = new double[101][2];
	public ArrayList<double[][]> cs =  new ArrayList<double[][]>();
	
	public irisFun(ArrayList<double[][]>cvals,double temp) {
		cs=cvals;
		t=(int) temp;
	}
	
	public Pair<RealVector, RealMatrix> value(RealVector in) {
		double[] din = in.toArray(),y = new double[4];
		y = irisfxn(din[0], din[1], din[2]);
		double d=0.0805,m=.5,b=0.1; 
		final RealVector ydata = new ArrayRealVector(y);
		double[] resultd1 = irisfxn(din[0]-d, din[1], din[2]),
				resultd2 = irisfxn(din[0]+d, din[1], din[2]),
				resultm1 = irisfxn(din[0], din[1]-m, din[2]),
				resultm2 = irisfxn(din[0], din[1]+m, din[2]),
				resultb1 = irisfxn(din[0], din[1], din[2]-b),
				resultb2 = irisfxn(din[0], din[1], din[2]+b),
				r1 = new double[4],r2 = new double[4],r3 = new double[4];
		
		for(int i=0;i<4;i++) {
			r1[i]=(resultd1[i]-resultd2[i])/(2*d);
			r2[i]=(resultm1[i]-resultm2[i])/(2*m);
			r3[i]=(resultb1[i]-resultb2[i])/(2*b);
		}
		
		final RealMatrix jacobian = new Array2DRowRealMatrix(4,3);
		jacobian.setColumnVector(0, new ArrayRealVector(r1));
		jacobian.setColumnVector(1, new ArrayRealVector(r2));
		jacobian.setColumnVector(2, new ArrayRealVector(r3));
		
		Pair<RealVector, RealMatrix> v = new Pair<RealVector, RealMatrix>(ydata, jacobian);
		
		return v;
	}
	
	public double fresnel(double n1,double n2,double n3,double d,double l){
		double r0,r1,D,req=0;
		r0=(n1-n2)/(n1+n2);
		r1=(n2-n3)/(n2+n3);
		D=((Math.PI)*2*d*n2)/l;
		Complex mid,id;
		id= new Complex(0.0,D);
		mid= new Complex(0.0,D);
		mid=mid.multiply(-1);
		Complex meid=mid.exp();
		Complex eid=id.exp();
		Complex numer=((meid.multiply(r0)).add((eid.multiply(r1))));
		Complex denom=meid.add(eid.multiply(r0*r1));
		req=((numer.divide(denom)).abs());
		return req;
	}
	
	public double[] irisfxn(double start,double m,double b){
		double sirefract,sirefract2,sio2refract,rsivalue,rvalue,s;
		double[] result = {0,0,0,0}, im = new double[251], mir = new double[251];
		generate_LUT gn = new generate_LUT();
		for(int j=0;j<4;j++) {
			int ct=0;
			for(double i=.4;i<.651;i+=0.001){
				sirefract=gn.interpolate(risi,i,s2);
				sirefract2=gn.SiRI(i,t);
				sio2refract=gn.SiO2RI(i,t);
				rsivalue=(fresnel(1,1,sirefract,start,i));
				rvalue=(fresnel(1,sio2refract,sirefract2,start,i));
				s=(gn.interpolate(cs.get(j),i,s1));
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