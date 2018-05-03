
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.util.FastMath;

import ij.IJ;

public class waterRI implements UnivariateFunction {
double a0,a1,a2,a3,a4,a5,a6,a7,lambda_uv,lambda_ir,T_ref,p_ref,lambda_ref,T_bar,p,p_bar,lambda_bar,luv2,lir2,n2,lbar2,pbar2;
double[] wtemps={0,4,10,20,30,40,50,60,70,80,90,100},plook={999.8,1000,999.7,998.2,995.7,992.2,988.1,983.2,977.8,971.8,965.3,958.4};
	
	public waterRI(double temp,double lambda) {
		a0 = .2442577733;
		a1 = 9.74634476e-3;
		a2 = -3.73234996e-3;
		a3=2.68678472e-4;
		a4=1.58920570e-3;
		a5 = 2.45934259e-3;
		a6 = .900704920;
		a7 = -1.66626219e-2;
		lambda_uv=.2292020;
		lambda_ir=5.432937;
		
		T_ref = 273.15;
		p_ref = 1000;
		lambda_ref = .589;
		
		T_bar = (temp+T_ref)/T_ref;
		p = interpolate2(wtemps,plook, temp, 12);
		p_bar = p/p_ref;
		lambda_bar = lambda/lambda_ref;
	}

	public double value(double n) { 
		luv2 = FastMath.pow(lambda_uv, 2);
		lir2 = FastMath.pow(lambda_ir, 2);
		n2 = FastMath.pow(n, 2);
		lbar2 = FastMath.pow(lambda_bar, 2);
		pbar2 = FastMath.pow(p_bar, 2);
		double result = (((n2-1)/(n2+2))*(1/p_bar))-(a0+(a1*p_bar)+(a2*T_bar)+(a3*(lbar2)*T_bar)+(a4/lbar2)+(a5/(lbar2-luv2))+(a6/(lbar2-lir2))+(a7*pbar2));
		return result;
	}
	
	public double interpolate2(double data1[],double data2[],double input, int size){
		int ind=-1;

		for(int i=0;i<=size-1;i++){
			if(input>=data1[i] && input<=data1[i+1])
				ind=i;
		}

		if(ind!=-1){
			double x1 = data1[ind];
			double x2 = data1[ind+1];
			double y1 = data2[ind];
			double y2 = data2[ind+1];
			return ((((y2-y1)/(x2-x1))*(input-x1))+y1);
		}else {
			return 0;
		}
	}

}
