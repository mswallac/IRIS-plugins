import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.ImageCalculator;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.commons.math3.fitting.*;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.analysis.*;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.lang.ArrayUtils;

public class generate_LUT implements PlugIn {
	public ImagePlus imp;
	public boolean canContinue,didCancel;
	public int s1=2501,s2=129,s2p=101,thickness,above,below,increment;
	public double[] filmavgs = new double[4],refavgs = new double[4],ydata1 = new double[4];
	public double[][] c1 = new double[2501][2],c2 = new double[2501][2],c3 = new double[2501][2],c4 = new double[2501][2],
			risi = new double[129][2],risio2 = new double[101][2],ripmma = new double[101][2];
	public ArrayList<Float> univ = new ArrayList<Float>();
	public ArrayList<double[][]> cs =  new ArrayList<double[][]>();
	public JFrame roiguide;
	public WeightedObservedPoints points = new WeightedObservedPoints();
	public JLabel refLabel,filmLabel;
	SimpleRegression sr = new SimpleRegression(true);


	public void run(String arg){
		imp = IJ.getImage();

        if(!(imp.getNChannels()==4)||!(imp.getNFrames()==1)) IJ.error("Use 4 channel, single frame image!");
        getRef();
        loaddata();
        getParams();
        ydata1=irisfun(((double)(thickness))/1000,1,0);
        
        for(int i=0;i<4;i++){
        	sr.addData(filmavgs[i]/refavgs[i],ydata1[i]);
        	IJ.log(""+(filmavgs[i]/refavgs[i])+" "+ydata1[i]);
        }

        
		//start by getting fit params for irisfun
		//for d (start)+/-(look above/below)
		// calculate I = sum((R*s)^2)/sum((rSi*s)^2)
		//lut will be [d I]?
        
        
	    
        double[][] ic = new double[(above+below)+1][4];
        double[] d = new double[(above+below)+1], p = {sr.getSlope(),sr.getIntercept()};
        int ct=0;
        IJ.log("m: "+p[0]+" b: "+p[1]);
        /*
	    for(double i=(thickness-below);i<=(thickness+above);i+=increment) {
	    	double[] values = new double[4];
        	values=irisfun((i/1000),p[0],p[1]);
        	for(int j=0;j<4;j++) {
        		ic[ct][j]=values[j];
        	}
        	d[ct] = (i);
        	ct++;
        }
        
		//write diff/sum function -> bestColor()?
	    //find best color
	    //make lut
	    //deal with reference file loading
	    //and LUT file saving
	    //resolve discrepancies between this and ML app

		RealMatrix mat = new Array2DRowRealMatrix(ic);
		int bestc = bestColor(ic);
		
	    makeLUT(mat.getColumn(bestc),d);
	    */
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

	public double interpolate(double data[][],double input, int size){
		int ind=-1;

		for(int i=0;i<=size-1;i++){
			if(input>data[i][0] && input<data[i+1][0])
				ind=i;
		}

		if(ind!=-1){
			double x1 = data[ind][0];
			double x2 = data[ind+1][0];
			double y1 = data[ind][1];
			double y2 = data[ind+1][1];
			return ((((y2-y1)/(x2-x1))*(input-x1))+y1);
		}else {
			return 0;
		}
	}
	
	public double interpolate2(double data1[],double data2[],double input, int size){
		int ind=-1;

		for(int i=0;i<=size-1;i++){
			if(input>data1[i] && input<data1[i+1])
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
	
	public void getParams() {
		GenericDialog gd = new GenericDialog("Generate LUT: ");
		gd.addNumericField("Approx. T (nm):", 100, 1);
		gd.addNumericField("Look above:", 10, 1);
		gd.addNumericField("Look below:", 10, 1);
		gd.addNumericField("Increment (nm):", 1, 1);
		
		gd.showDialog();
		if (gd.wasCanceled())
			IJ.error("GenerateLUT cancelled.");
		
		thickness=(int)gd.getNextNumber();
		above=(int)gd.getNextNumber();
		below=(int)gd.getNextNumber();
		increment=(int)gd.getNextNumber();
		
		
		return;
	}
	
	public void getRef(){
		JPanel selectPanel = new JPanel();
		selectPanel.setLayout(new GridLayout(2,1,10,10));
		selectPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		imp.getWindow().toFront();
		IJ.setTool(Toolbar.RECTANGLE);

		JButton referenceButton = new JButton("Get reference intensity");
		referenceButton.setEnabled(true);
		referenceButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				for(int i=1;i<5;i++){
					imp.setC(i);
					refavgs[i-1]=takeroimean(imp.getRoi());
				}
				imp.setC(1);
			}
		});
		selectPanel.add(referenceButton);

		JButton filmButton = new JButton("Get film intensities");
		filmButton.setEnabled(true);
		filmButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				for(int i=1;i<5;i++){
					imp.setC(i);
					filmavgs[i-1]=takeroimean(imp.getRoi());
				}
				imp.setC(1);
			}
		});
		selectPanel.add(filmButton);

		// Create the buttonPanel, which has the "Cancel" and "OK" buttons
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(1,2,20,20));
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		JButton cancelButton = new JButton("Cancel");
		cancelButton.setEnabled(true);
		cancelButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				didCancel = true;
				roiguide.dispose();
			}
		});
		buttonPanel.add(cancelButton);

		JButton okButton = new JButton("OK");
		okButton.setEnabled(true);
		okButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				canContinue=true;
				roiguide.dispose();	
			}
		});
		buttonPanel.add(okButton);

		// Create and populate the JFrame
		roiguide = new JFrame("");
		roiguide.getContentPane().add(selectPanel, BorderLayout.NORTH);
		roiguide.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		roiguide.setLocation(350,410);
		roiguide.setVisible(true);
		roiguide.setResizable(false);
		roiguide.pack();

		while (!canContinue){
			try {
				Thread.sleep(200);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}

	public double takeroimean(Roi roi) {
		ImagePlus imp = IJ.getImage();
		if (roi!=null && !roi.isArea()) roi = null;
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor mask = roi!=null?roi.getMask():null;
		Rectangle r = roi!=null?roi.getBounds():new Rectangle(0,0,ip.getWidth(),ip.getHeight());
		double sum = 0;
		int count = 0;
		for(int y=0; y<r.height; y++) {
			for (int x=0; x<r.width; x++) {
				if (mask==null||mask.getPixel(x,y)!=0) {
					count++;
					sum += ip.getPixelValue(x+r.x, y+r.y);
				}
			}
		}
		return (sum/count);
	}

	public void loaddata(){
		TextWindow tw = new TextWindow("E:/OCN/ledspectra.txt",1,1);
		TextPanel tp = tw.getTextPanel();

		for(int i=0;i<=tp.getLineCount()-1;i++){
			String s[] = tp.getLine(i).split("	");
			c1[i][0]=(Float.parseFloat(s[0]));
			c1[i][1]=(Float.parseFloat(s[1]));
			c2[i][0]=(Float.parseFloat(s[2]));
			c2[i][1]=(Float.parseFloat(s[3]));
			c3[i][0]=(Float.parseFloat(s[4]));
			c3[i][1]=(Float.parseFloat(s[5]));
			c4[i][0]=(Float.parseFloat(s[6]));
			c4[i][1]=(Float.parseFloat(s[7]));
			s1=i;
		}
		
		cs.add(c1);
		cs.add(c2);
		cs.add(c3);
		cs.add(c4);
		
		tw.dispose();
		tw = new TextWindow("E:/OCN/refractinds.txt",1,1);
		tp = tw.getTextPanel();

		for(int i=0;i<=tp.getLineCount()-1;i++){
			String s[] = tp.getLine(i).split("	");
			risi[i][0]=(Float.parseFloat(s[0]));
			risi[i][1]=(Float.parseFloat(s[1]));
			if(i<=100){
				risio2[i][0]=(Float.parseFloat(s[2]));
				risio2[i][1]=(Float.parseFloat(s[3]));
				ripmma[i][0]=(Float.parseFloat(s[4]));
				ripmma[i][1]=(Float.parseFloat(s[5]));
			}
		}
		tw.dispose();
	}
	
	
	public double[] irisfun(double start,double p1,double p2){
		double sirefract,sirefract2,sio2refract,rsivalue,rvalue,m,s,I,isum,msum;
		double[] result = {0,0,0,0}, im = new double[251], mir = new double[251];
		
		for(int j=0;j<4;j++) {
			int ct=0;
			for(double i=.4;i<.651;i+=0.001){
				sirefract=interpolate(risi,i,s2);
				sirefract2=SiRI(i,25);
				sio2refract=SiO2RI(i,25);
				rsivalue=(fresnel(1,1,sirefract,start,i));
				rvalue=(fresnel(1,sio2refract,sirefract2,start,i));
				s=(interpolate(cs.get(j),i,s1));
				s=(FastMath.sqrt(s));
				if(s>=0) {
					mir[ct]=FastMath.pow(((s)*rsivalue),2);
					im[ct]=FastMath.pow(((s)*rvalue),2);
				}else {
					m=0;
					I=0;
				}
				ct++;
				IJ.log(""+i+" "+rvalue+" "+rsivalue+" "+s);
			}
			result[j] = ((StatUtils.sum(im))/(StatUtils.sum(mir)));
		}
		
		IJ.log(" "+result[0]+" "+result[1]+" "+result[2]+" "+result[3]);
		
		double[] result1 = {((result[0]*p1)+p2),((result[1]*p1)+p2),((result[2]*p1)+p2),((result[3]*p1)+p2)};
		
		return (result1);
		
	}
	
	public int bestColor(double[][] input) {
		RealMatrix mat = new Array2DRowRealMatrix(input);
		double[] diffsums = new double[4];
		int maxind;
		double max;
		for(int i=0;i<4;i++) {
			diffsums[i]=diffsum(mat.getColumn(i));
		}
		max = StatUtils.max(diffsums);
		maxind = ArrayUtils.indexOf(diffsums, max);
		return maxind;
	}
	
	public double diffsum(double[] in) {
		double sum=0;
		for(int i=0;i<in.length-1;i++) {
			sum+=(in[i+1]-in[i]);
		}
		return sum;
	}
	
	public void makeLUT(double[] bestColor, double[] d) {
		TextWindow lut = new TextWindow("LUT","",400,800);
		for(int i=0;i<bestColor.length;i++)
			lut.getTextPanel().appendLine(d[i]+"    "+bestColor[i]);
	}
	
	public double SiRI(double lambda,double temp) {
		double[] wavelengths={0.2638,0.2652,0.2666,0.2681,0.2695,0.2710,0.2725,0.2740,0.2755,0.2771,0.2786,0.2802,0.2818,0.2834,0.2850,0.2867,0.2883,0.2900,0.2917,0.2935,0.2952,0.2970,0.2988,0.3006,0.3024,0.3043,0.3061,0.3080,0.3100,0.3119,0.3139,0.3159,0.3179,0.3200,0.3220,0.3241,0.3263,0.3284,0.3306,0.3328,0.3351,0.3374,0.3397,0.3420,0.3444,0.3468,0.3493,0.3517,0.3542,0.3568,0.3594,0.3620,0.3647,0.3674,0.3701,0.3729,0.3757,0.3786,0.3815,0.3844,0.3875,0.3905,0.3936,0.3967,0.3999,0.4032,0.4065,0.4099,0.4133,0.4168,0.4203,0.4239,0.4275,0.4312,0.4350,0.4389,0.4428,0.4468,0.4509,0.4550,0.4592,0.4635,0.4679,0.4723,0.4769,0.4815,0.4862,0.4910,0.4959,0.5009,0.5061,0.5113,0.5166,0.5220,0.5276,0.5333,0.5391,0.5450,0.5510,0.5572,0.5636,0.5700,0.5767,0.5835,0.5904,0.5975,0.6048,0.6123,0.6199,0.6278,0.6358,0.6441,0.6525,0.6612,0.6702,0.6794,0.6888,0.6985,0.7085,0.7187,0.7293,0.7402,0.7514,0.7630,0.7749,0.7872,0.7999,0.8130,0.8266},
				n20={1.8250,1.8540,1.8990,1.9540,2.0140,2.0990,2.1880,2.2930,2.4260,2.5740,2.7230,2.8770,3.0370,3.2320,3.4060,3.6680,3.9260,4.2050,4.4540,4.6590,4.7910,4.8850,4.9280,4.9850,4.9860,5.0090,5.0250,5.0240,5.0290,5.0340,5.0390,5.0350,5.0320,5.0510,5.0680,5.0660,5.0910,5.1200,5.1350,5.1590,5.1770,5.2180,5.2470,5.2770,5.3100,5.3630,5.4290,5.4870,5.6010,5.7520,5.9670,6.2510,6.5630,6.8210,6.9890,7.0040,6.8990,6.7350,6.5480,6.3690,6.1910,6.0410,5.8970,5.7640,5.6490,5.5430,5.4500,5.3560,5.2790,5.1970,5.1260,5.0560,4.9940,4.9330,4.8720,4.8250,4.7700,4.7220,4.6770,4.6310,4.5910,4.5490,4.5100,4.4710,4.4350,4.4020,4.3680,4.3370,4.3090,4.2810,4.2520,4.2240,4.2000,4.1770,4.1530,4.1310,4.1090,4.0890,4.0680,4.0490,4.0290,4.0120,3.9950,3.9770,3.9600,3.9441,3.9291,3.9144,3.9000,3.8860,3.8723,3.8589,3.8450,3.8330,3.8205,3.8084,3.7960,3.7851,3.7739,3.7631,3.7530,3.7423,3.7324,3.7228,3.7160,3.7046,3.6960,3.6877,3.6780},
				n100={1.9180,1.9730,2.0170,2.0560,2.1110,2.1730,2.2660,2.3520,2.4490,2.5760,2.7040,2.8800,3.0220,3.1830,3.3590,3.5760,3.7960,4.0480,4.3060,4.5370,4.7190,4.8820,4.9750,5.0350,5.0700,5.1020,5.1210,5.1210,5.1250,5.1470,5.1400,5.1340,5.1250,5.1490,5.1620,5.1730,5.1880,5.1990,5.2080,5.2220,5.2400,5.2700,5.3040,5.3290,5.3680,5.4080,5.4570,5.5100,5.6030,5.6870,5.8350,5.9910,6.1840,6.4270,6.6370,6.8140,6.8520,6.8010,6.6740,6.5140,6.3600,6.2040,6.0460,5.9130,5.7880,5.6760,5.5760,5.4770,5.3830,5.3010,5.2250,5.1560,5.0870,5.0180,4.9570,4.9030,4.8450,4.7920,4.7460,4.7000,4.6540,4.6120,4.5710,4.5290,4.4920,4.4570,4.4220,4.3880,4.3590,4.3260,4.2960,4.2700,4.2410,4.2150,4.1930,4.1700,4.1460,4.1260,4.1030,4.0830,4.0640,4.0450,4.0270,4.0080,3.9920,3.9730,3.9537,3.9400,3.9280,3.9110,3.8960,3.8810,3.8660,3.8550,3.8440,3.8330,3.8220,3.8049,3.7980,3.7838,3.7730,3.7600,3.7530,3.7430,3.7331,3.7230,3.7120,3.7040,3.6951},
				temps={20,100}, nvals = {interpolate2(wavelengths, n20, lambda, s2),interpolate2(wavelengths, n100, lambda, s2)};

		return (interpolate2(temps,nvals,temp,2));
	}
	
	public double SiO2RI(double lambda,double T) {
		double A = (1.31552+6.90754e-6*T), B = (.788404+2.35835e-5*T), C = (.011099+5.84758e-7*T),
				D = (0.91316+5.48368e-7*T), E = 100;

		return (FastMath.sqrt((A)+(B/(1-(C/FastMath.pow(lambda,2))))+(D/(1-(E/FastMath.pow(lambda,2))))));
	}
	
	
}
