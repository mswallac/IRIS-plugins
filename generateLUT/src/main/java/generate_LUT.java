import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.Toolbar;
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
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.analysis.*;
import org.apache.commons.math3.complex.Complex;

public class generate_LUT implements PlugIn {
	public ImagePlus imp;
	public boolean canContinue,didCancel;
	public int s1=2501,s2=129,s2p=101;
	public double[] filmavgs = new double[4],refavgs = new double[4],ydata1 = new double[4];
	public double[][] c1 = new double[2501][2],c2 = new double[2501][2],c3 = new double[2501][2],c4 = new double[2501][2];
	public double[][] risi = new double[129][2],risio2 = new double[101][2],ripmma = new double[101][2];
	public ArrayList<Float> univ = new ArrayList<Float>();
	public Collection<WeightedObservedPoint> step1;
	public JFrame roiguide;
	public JLabel refLabel,filmLabel;


	public void run(String arg){
		imp = IJ.getImage();

        if(!(imp.getNChannels()==4)||!(imp.getNFrames()==1)) IJ.error("Use 4 channel, single frame image!");
        getRef();
        loaddata();
        ydata1=irisfun(.1,1,0);
        
        /*for(int i=0;i<=4;i++){
        	WeightedObservedPoint pt = new WeightedObservedPoint(1.0, ydata1[0],filmavgs[0]);
        	step1.
        }
        
        
        

	     // Instantiate a third-degree polynomial fitter.
	     final PolynomialCurveFitter fitter = PolynomialCurveFitter.create(1);
	
	     // Retrieve fitted parameters (coefficients of the polynomial function).
	     final double[] coeff = fitter.fit(step1);
	     for(int i=0;i<coeff.length;i++)
	        	IJ.log(""+coeff[i]);
	     */
        
	     IJ.log("C1: "+ydata1[0]+"   C2: "+ydata1[1]+"   C3: "+ydata1[2]+"    C4: "+ydata1[3]);
		
		//start by getting fit params for irisfun
		//for d (start)+/-(look above/below)
		// calculate I = sum((R*s)^2)/sum((rSi*s)^2)
		//lut will be [d I]?
        
        
		
		
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
		req=(numer.divide(denom)).abs();
		return req;
	}

	//figure out how matlab interpolates and then go from there
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

	public void getRef(){
		JPanel selectPanel = new JPanel();
		selectPanel.setLayout(new GridLayout(1,2,0,0));
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
				imp.setC(1);;
			}
		});



		filmLabel = new JLabel("");
		selectPanel.add(filmButton);
		selectPanel.add(filmLabel);

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
		roiguide = new JFrame("Get film and reference reflectance values:");
		roiguide.getContentPane().add(selectPanel, BorderLayout.NORTH);
		roiguide.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		roiguide.setLocation(400,400);
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
	}
	
	
	public double[] irisfun(double start,double p1,double p2){
		double I1,I2,I3,I4,sirefract,rsivalue,rvalue,s1value,s2value,s3value,s4value,m1,m2,m3,m4;
		double[] isums={0,0,0,0},msums={0,0,0,0}, m = new double[4], I = new double[4],s = new double[4];
		for(double i=.4;i<.651;i+=0.001){
			sirefract=interpolate(risi,i,s2);
			rsivalue=FastMath.pow(fresnel(1,1.45,sirefract,start,i),2);
			rvalue=FastMath.pow(fresnel(1,1.45,4.2,start,i),2);
			s[0]=FastMath.sqrt(interpolate(c1,i,s1));
			s[1]=FastMath.sqrt(interpolate(c2,i,s1));
			s[2]=FastMath.sqrt(interpolate(c3,i,s1));
			s[3]=FastMath.sqrt(interpolate(c4,i,s1));
			for(int j=0;j<4;j++) {
				m[j]=FastMath.pow((s[j]*rsivalue),2);
				I[j]=FastMath.pow((s[j]*rvalue),2);
				msums[j]+=m[j];
				isums[j]+=I[j];
			}
		}
		for(int j=0;j<4;j++)
			IJ.log("I: "+isums[j]+" M:"+msums[j]);
		double[] result = {(isums[0]/msums[0]),(isums[1]/msums[1]),(isums[2]/msums[2]),(isums[3]/msums[3])};
		IJ.log("fitting params: " +p1+","+p2);
		double[] result1 = {((result[0]*p1)+p2),((result[1]*p1)+p2),((result[2]*p1)+p2),((result[3]*p1)+p2)};
		return (result1);
	}
}
