package us.ihmc.robotEnvironmentAwareness.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import javax.vecmath.Point2d;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.quickhull3d.Point3d;
import com.github.quickhull3d.QuickHull3D;
import com.sun.javafx.application.PlatformImpl;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import us.ihmc.commons.MutationTestFacilitator;
import us.ihmc.commons.PrintTools;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.euclid.geometry.LineSegment2D;
import us.ihmc.euclid.geometry.LineSegment3D;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.javaFXToolkit.messager.JavaFXMessager;
import us.ihmc.javaFXToolkit.messager.SharedMemoryJavaFXMessager;
import us.ihmc.messager.Messager;
import us.ihmc.messager.SharedMemoryMessager;
import us.ihmc.robotEnvironmentAwareness.polygonizer.PolygonizerManager;
import us.ihmc.robotEnvironmentAwareness.polygonizer.PolygonizerVisualizerUI;
import us.ihmc.robotEnvironmentAwareness.ui.io.PlanarRegionDataImporter;
import us.ihmc.robotics.geometry.PlanarRegionsList;
import us.ihmc.robotics.random.RandomGeometry;

public class ConcaveHullTestBasics
{
	protected static final int ITERATIONS = 10000;
	protected static boolean VISUALIZE = false;
	protected static final double EPS = 1.0e-5;

	protected Messager messager;
	protected MutableBoolean uiIsGoingDown = new MutableBoolean(false);

	protected static List<Point2D> pointcloud2D = null;
	protected static List<LineSegment2D> lineConstraints2D = null;
	protected static List<Point3D> pointcloud3D = null;
	protected static List<LineSegment3D> lineConstraints3D = null;

	protected static boolean baseClassInitialized = false;

	private static final boolean VERBOSE = true;
	private Window ownerWindow;

	private PlanarRegionsList loadedPlanarRegions = null;

	public void attachMessager(Messager messager)
	{
		this.messager = messager;
	}

	public void setMainWindow(Window ownerWindow)
	{
		this.ownerWindow = ownerWindow;
	}

	@SuppressWarnings("deprecation")
	public void loadPlanarRegions()
	{
		loadedPlanarRegions = PlanarRegionDataImporter.importUsingFileChooser(ownerWindow);

		if (loadedPlanarRegions != null)
		{
			if (VERBOSE)
				PrintTools.info(this, "Loaded planar regions, broadcasting data.");
			//			messager.submitMessage(FootstepPlannerMessagerAPI.GlobalResetTopic, true);
			//			messager.submitMessage(FootstepPlannerMessagerAPI.PlanarRegionDataTopic, loadedPlanarRegions);
		}
		else
		{
			if (VERBOSE)
				PrintTools.info(this, "Failed to load planar regions.");
		}
	}

	public static List<Point2D> getPointcloud2D()
	{
		return pointcloud2D;
	}

	public static List<LineSegment2D> getLineConstraints2D()
	{
		return lineConstraints2D;
	}

	public static List<Point3D> getPointcloud3D()
	{
		return pointcloud3D;
	}

	public static List<LineSegment3D> getLineConstraints3D()
	{
		return lineConstraints3D;
	}

	public boolean initializeBaseClass()
	{
		//		if(baseClassInitialized)
		//			return true;

		// A random pointCloud generated in Matlab.
		// This cloud is generated by subtracting a rectangular section of the original
		// cloud,
		// creating a large pocket on the right side. The resulting hull should look
		// somewhat like a crooked 'C'.

		pointcloud3D = new ArrayList<>();
		pointcloud3D.add(new Point3D(0.417022005, 0.326644902, 0));
		pointcloud3D.add(new Point3D(0.000114375, 0.885942099, 0));
		pointcloud3D.add(new Point3D(0.302332573, 0.35726976, 0));
		pointcloud3D.add(new Point3D(0.146755891, 0.908535151, 0));
		pointcloud3D.add(new Point3D(0.092338595, 0.623360116, 0));
		pointcloud3D.add(new Point3D(0.186260211, 0.015821243, 0));
		pointcloud3D.add(new Point3D(0.345560727, 0.929437234, 0));
		pointcloud3D.add(new Point3D(0.396767474, 0.690896918, 0));
		pointcloud3D.add(new Point3D(0.538816734, 0.99732285, 0));
		pointcloud3D.add(new Point3D(0.419194514, 0.172340508, 0));
		pointcloud3D.add(new Point3D(0.6852195, 0.13713575, 0));
		pointcloud3D.add(new Point3D(0.20445225, 0.932595463, 0));
		pointcloud3D.add(new Point3D(0.027387593, 0.066000173, 0));
		pointcloud3D.add(new Point3D(0.417304802, 0.753876188, 0));
		pointcloud3D.add(new Point3D(0.558689828, 0.923024536, 0));
		pointcloud3D.add(new Point3D(0.140386939, 0.711524759, 0));
		pointcloud3D.add(new Point3D(0.198101489, 0.124270962, 0));
		pointcloud3D.add(new Point3D(0.800744569, 0.019880134, 0));
		pointcloud3D.add(new Point3D(0.968261576, 0.026210987, 0));
		pointcloud3D.add(new Point3D(0.313424178, 0.028306488, 0));
		pointcloud3D.add(new Point3D(0.876389152, 0.860027949, 0));
		pointcloud3D.add(new Point3D(0.085044211, 0.552821979, 0));
		pointcloud3D.add(new Point3D(0.039054783, 0.842030892, 0));
		pointcloud3D.add(new Point3D(0.16983042, 0.124173315, 0));
		pointcloud3D.add(new Point3D(0.098346834, 0.585759271, 0));
		pointcloud3D.add(new Point3D(0.421107625, 0.969595748, 0));
		pointcloud3D.add(new Point3D(0.533165285, 0.018647289, 0));
		pointcloud3D.add(new Point3D(0.691877114, 0.800632673, 0));
		pointcloud3D.add(new Point3D(0.315515631, 0.232974274, 0));
		pointcloud3D.add(new Point3D(0.686500928, 0.807105196, 0));
		pointcloud3D.add(new Point3D(0.018288277, 0.863541855, 0));
		pointcloud3D.add(new Point3D(0.748165654, 0.136455226, 0));
		pointcloud3D.add(new Point3D(0.280443992, 0.05991769, 0));
		pointcloud3D.add(new Point3D(0.789279328, 0.121343456, 0));
		pointcloud3D.add(new Point3D(0.103226007, 0.044551879, 0));
		pointcloud3D.add(new Point3D(0.447893526, 0.107494129, 0));
		pointcloud3D.add(new Point3D(0.293614148, 0.71298898, 0));
		pointcloud3D.add(new Point3D(0.287775339, 0.559716982, 0));
		pointcloud3D.add(new Point3D(0.130028572, 0.01255598, 0));
		pointcloud3D.add(new Point3D(0.019366958, 0.07197428, 0));
		pointcloud3D.add(new Point3D(0.678835533, 0.96727633, 0));
		pointcloud3D.add(new Point3D(0.211628116, 0.568100462, 0));
		pointcloud3D.add(new Point3D(0.265546659, 0.203293235, 0));
		pointcloud3D.add(new Point3D(0.491573159, 0.252325745, 0));
		pointcloud3D.add(new Point3D(0.053362545, 0.743825854, 0));
		pointcloud3D.add(new Point3D(0.574117605, 0.195429481, 0));
		pointcloud3D.add(new Point3D(0.146728575, 0.581358927, 0));
		pointcloud3D.add(new Point3D(0.589305537, 0.970019989, 0));
		pointcloud3D.add(new Point3D(0.69975836, 0.846828801, 0));
		pointcloud3D.add(new Point3D(0.102334429, 0.239847759, 0));
		pointcloud3D.add(new Point3D(0.414055988, 0.493769714, 0));
		pointcloud3D.add(new Point3D(0.41417927, 0.8289809, 0));
		pointcloud3D.add(new Point3D(0.049953459, 0.156791395, 0));
		pointcloud3D.add(new Point3D(0.535896406, 0.018576202, 0));
		pointcloud3D.add(new Point3D(0.663794645, 0.070022144, 0));
		pointcloud3D.add(new Point3D(0.137474704, 0.988616154, 0));
		pointcloud3D.add(new Point3D(0.139276347, 0.579745219, 0));
		pointcloud3D.add(new Point3D(0.397676837, 0.550948219, 0));
		pointcloud3D.add(new Point3D(0.165354197, 0.745334431, 0));
		pointcloud3D.add(new Point3D(0.34776586, 0.264919558, 0));
		pointcloud3D.add(new Point3D(0.750812103, 0.066334834, 0));
		pointcloud3D.add(new Point3D(0.348898342, 0.066536481, 0));
		pointcloud3D.add(new Point3D(0.269927892, 0.260315099, 0));
		pointcloud3D.add(new Point3D(0.895886218, 0.804754564, 0));
		pointcloud3D.add(new Point3D(0.42809119, 0.193434283, 0));
		pointcloud3D.add(new Point3D(0.62169572, 0.92480797, 0));
		pointcloud3D.add(new Point3D(0.114745973, 0.26329677, 0));
		pointcloud3D.add(new Point3D(0.949489259, 0.065961091, 0));
		pointcloud3D.add(new Point3D(0.449912133, 0.735065963, 0));
		pointcloud3D.add(new Point3D(0.408136803, 0.907815853, 0));
		pointcloud3D.add(new Point3D(0.23702698, 0.931972069, 0));
		pointcloud3D.add(new Point3D(0.903379521, 0.013951573, 0));
		pointcloud3D.add(new Point3D(0.002870327, 0.616778357, 0));
		pointcloud3D.add(new Point3D(0.617144914, 0.949016321, 0));

		lineConstraints3D = new ArrayList<>();
		lineConstraints3D.add(new LineSegment3D(0.0, -0.5, 0.0, 0.0, 0.5, 0.0));
		lineConstraints3D.add(new LineSegment3D(2.0, -0.5, 0.0, 2.0, 0.5, 0.0));
		lineConstraints3D.add(new LineSegment3D(0.0, 0.5, 0.0, 2.0, 0.5, 0.0));
		lineConstraints3D.add(new LineSegment3D(0.0, -0.5, 0.0, 2.0, -0.5, 0.0));
		// System.out.println("ConcaveHullToolsTest: lineConstraints: " + lineConstraints.toString());

		pointcloud2D = new ArrayList<>();
		for (Point3D i : pointcloud3D)
			pointcloud2D.add(new Point2D(i.getX(), i.getY()));

		lineConstraints2D = new ArrayList<>();
		for (LineSegment3D i : lineConstraints3D)
			lineConstraints2D.add(new LineSegment2D(i.getFirstEndpointX(), i.getFirstEndpointY(), i.getSecondEndpointX(), i.getSecondEndpointY()));

		sombreroInitialized = createSombrero(-5.0, 5.0, 51);

		sombreroCollection = new ConcaveHullCollection();
		sombreroCollection.add(sombrero);
		sombreroHull = new ConcaveHull(sombrero);

		baseClassInitialized = true;
		return baseClassInitialized;
	}

	ConcaveHullCollection sombreroCollection = null;
	ConcaveHull sombreroHull = null;

	protected static boolean sombreroInitialized = false;
	protected static List<Point2D> sombrero = null;
	protected static List<LineSegment2D> sombreroConstraints2D = null;
	protected static List<Point3D> sombrero3D = null;
	protected static List<LineSegment3D> sombreroConstraints3D = null;
	protected int max, min1, min2;

	protected Point2D hatMax = new Point2D(), hatMin1 = new Point2D(), hatMin2 = new Point2D();
	protected double depth1, depth2;
	Point3D leftLowerCornerPoint;
	Point3D rightLowerCornerPoint;
	double[] x;
	double[] y;
	int xlen;

	public boolean createSombrero(double lb, double ub, int n)
	{
		max = -1;
		min1 = -1;
		min2 = -1;

		x = linspace(lb, ub, n);
		y = sombrero(x);
		xlen = x.length;

		//for(int i=0; i<xlen; i++)
		//	System.out.printf("%3.5f ", y[i]);

		double highestY = Double.MIN_VALUE;
		double lowestY1 = Double.MAX_VALUE;
		double lowestY2 = Double.MAX_VALUE;
		leftLowerCornerPoint = new Point3D();
		rightLowerCornerPoint = new Point3D();

		// locate the peak
		for (int i = 0; i < xlen; i++)
		{
			if (y[i] > highestY)
			{
				highestY = y[i];
				max = i;
			}
		}

		// find the first minimum
		for (int i = 0; i < max; i++)
		{
			if (y[i] < lowestY1)
			{
				lowestY1 = y[i];
				min1 = i;
			}
		}

		// find the second minimum
		for (int i = max; i < xlen; i++)
		{
			if (y[i] < lowestY2)
			{
				lowestY2 = y[i];
				min2 = i;
			}
		}

		y[min2] += 10 * EPS; //Raise min2 a little

		hatMax.set(x[max], y[max]);
		hatMin1.set(x[min1], y[min1]);
		hatMin2.set(x[min2], y[min2]);

		double dx = hatMax.getX() - hatMin1.getX();
		double dy = hatMax.getY() - hatMin1.getY();
		depth1 = Math.sqrt(dx * dx + dy - dy);
		dx = hatMax.getX() - hatMin1.getX();
		dy = hatMax.getY() - hatMin1.getY();
		depth2 = Math.sqrt(dx * dx + dy - dy);

		//		System.out.printf("\n max, min1, min2  = %d %e %d %e %d %e " , max, y[max], min1, y[min1], min2, y[min2] );

		leftLowerCornerPoint.set(x[0], lowestY1 - 1, 0);
		rightLowerCornerPoint.set(x[xlen - 1], lowestY1 - 1, 0);

		sombrero3D = new ArrayList<>();
		sombrero3D.add(leftLowerCornerPoint);
		for (int i = 0; i < xlen; i++)
		{
			sombrero3D.add(new Point3D(x[i], y[i], 0));
		}
		sombrero3D.add(rightLowerCornerPoint);

		sombrero = new ArrayList<>();
		for (Point3D i : sombrero3D)
			sombrero.add(new Point2D(i.getX(), i.getY()));

		fillSombreroWithRandomPoints();
		
		// Print data to paste into Matlab, plot with scatter3(X,Z,Y,1);
		//printRow3D(sombrero3D, "\nX=[");
		//printRow3D(sombrero3D, "\nY=[");
		//printRow3D(sombrero3D, "\nZ=[");

		double k=5;
		Point3D C = new Point3D(-10.0, 5.0, 5.0);                     //%1xD  D dimensional Camera viewpoint.
		double param = 2.3;                            //%param - parameter for the algorithm. Indirectly sets the radius.
		Point3D[] p = new Point3D[sombrero3D.size()];
		for(int i=0; i< sombrero3D.size(); i++)
			p[i] = sombrero3D.get(i);
		
		List<Point3D> visible3D = hiddenPointRemoval( p, C, param );


		printRow3D(visible3D, "\nX=[");
		printRow3D(visible3D, "\nY=[");
		printRow3D(visible3D, "\nZ=[");

		//		System.out.println("\n"+sombrero2D);
		return true;
	}

	void printRow2D(List<Point2D> list, String tag)
	{
		double val = 0.0;
		System.out.printf(tag);
		for (int i = 0; i < list.size(); i++)
		{
			if (tag.startsWith("\nX"))
				val = list.get(i).getX();
			if (tag.startsWith("\nY"))
				val = list.get(i).getY();
			System.out.printf("%3.3f", val);
			if (i < list.size() - 1)
				System.out.printf(",");
		}
		System.out.printf("];");
	}
	
	void printRow3D(List<Point3D> list, String tag)
	{
		double val = 0.0;
		System.out.printf(tag);
		int n = list.size();
		for (int i = 0; i < n; i++)
		{
			if (tag.startsWith("\nX"))
				val = list.get(i).getX();
			if (tag.startsWith("\nY"))
				val = list.get(i).getY();
			if (tag.startsWith("\nZ"))
				val = list.get(i).getZ();
			System.out.printf("%3.3f", val);
			if (i < n - 1)
				System.out.printf(",");
			else
				System.out.printf("];");
		}
	}

	/*
	 * Some Matlab code to plot a mexicanHat...
	 * https://searchcode.com/codesearch/view/9537655/ function [psi,x] =
	 * mexihat(lb,ub,n) if (nargin < 3) usage('[psi,x] = mexihat(lb,ub,n)'); end
	 * if (n <= 0) error("n must be strictly positive"); end x =
	 * linspace(lb,ub,n); for i = 1:n psi(i) = (1-x(i)^2)*2/(sqrt(3)*pi^0.25) *
	 * exp(-x(i)^2/2); end end lb = -3; ub = 3; N = 20; [psi,xval] =
	 * mexihat(lb,ub,N); plot(xval,psi) title('Mexican Hat Wavelet');
	 */

	// Equivalent Java functions...	
	double[] linspace(double lb, double ub, int n)
	{
		if (n < 1)
			return null;

		double[] x = new double[n];
		for (int i = 0; i < n; i++)
		{
			x[i] = lb + i * (ub - lb) / (n - 1);
			//System.out.printf("\n %d %e ", i, x[i]);
		}
		return x;
	}

	double rickerWavelet(double x)
	{
		double y = 0.1*(1.0 - Math.pow(x, 2.0)) * 2.0 / (Math.sqrt(3.0) * Math.pow(Math.PI, 0.25)) * Math.exp(-Math.pow(x, 2.0) / 2.0);
		return y;
	}

	double staircaseFunction(double x)
	{
		double y=0.0;		
		if(x > -2) y= 0.0;
		if(x > -1) y= .25;
		if(x > 0)  y= .5;
		if(x > 1) y= .75;
		if(x > 2) y= 1;
		if(x > 3) y= .5;
		if(x > 4) y= 0.0;
		return y;
	}
	
	double[] sombrero(double[] x)
	{
		int n = x.length;
		double[] psi = new double[n];
		for (int i = 0; i < n; i++)
		{
			psi[i] = rickerWavelet(x[i]);
		}
		return psi;
	}

	protected Point2D calculateNextRandomPtInSombrero(Random randomX, Random randomY)
	{
		Point2D result = new Point2D();
		double x, y, fx, xmin, xmax, ymin, ymax;

		xmin = leftLowerCornerPoint.getX();
		xmax = rightLowerCornerPoint.getX();
		ymin = 0.1 * leftLowerCornerPoint.getY();

		x = xmin + (xmax - xmin) * randomX.nextDouble();
		fx = rickerWavelet(x);
		y = fx - Math.abs(ymin) * randomX.nextDouble();
		result.set(x, y);
		return result;
	}

	protected Point3D calculateNextRandomPtInSombrero3D(Random randomX, Random randomY)
	{
		Point3D result = new Point3D();
		double x, y, z, fx, xmin, xmax, ymin, ymax;

		xmin = leftLowerCornerPoint.getX();
		xmax = rightLowerCornerPoint.getX();
		ymin = 0.1 * leftLowerCornerPoint.getY();

		x = xmin + (xmax - xmin) * randomX.nextDouble();
		//fx = rickerWavelet(x);
		fx = staircaseFunction(x);
		y = fx - 2.5*Math.abs(ymin) * randomX.nextDouble();
		z = 1.0 * (randomX.nextDouble() - 0.5);
		result.set(x, y, z);
		return result;
	}

	int npoints = 30000;
	
	protected void fillSombreroWithRandomPoints()
	{
		Random randomX = new Random(0);
		Random randomY = new Random(0);
		for (int i = 0; i < npoints; i++)
		{
			Point3D nextRandomPoint3D = calculateNextRandomPtInSombrero3D(randomX, randomY);
			sombrero3D.add(nextRandomPoint3D);
		}
	}
	
//	function A = repmata(v, m, n)
//			A = ones(m,1) * v;
//			end

	
//	% HPR - Using HPR ("Hidden Point Removal) method, approximates a visible subset of points 
//	% as viewed from a given viewpoint.
//	% Usage:
//	% visiblePtInds=HPR(p,C,param)
//	%
//	% Input:
//	% p - NxD D dimensional point cloud.
//	% C - 1xD D dimensional viewpoint.
//	% param - parameter for the algorithm. Indirectly sets the radius.
//	%
//	% Output:
//	% visiblePtInds - indices of p that are visible from C.
//	%
//	% This code was written by Sagi Katz
//	% sagikatz@tx.technion.ac.il
//	% Technion, 2006.
//	% For more information, see "Direct Visibility of Point Sets", Katz S., Tal
//	% A. and Basri R., SIGGRAPH 2007, ACM Transactions on Graphics, Volume 26, Issue 3, August 2007.
//	%
//	% This method is patent pending.
//
//	function visiblePtInds = HPR(p,C,param)
//	dim = size(p,2);
//	numPts = size(p,1);
//	p = p-repmat(C,[numPts 1]);                                       %Move C to the origin
//	normp = sqrt(dot(p,p,2));                                         %Calculate ||p||
//	R = repmat(max(normp)*(10^param),[numPts 1]);                     %Sphere radius
//	P = p+2*repmat(R-normp,[1 dim]).*p./repmat(normp,[1 dim]);        %Spherical flipping
//	visiblePtInds = unique(convhulln([P;zeros(1,dim)]));              %convex hull
//	visiblePtInds(visiblePtInds==numPts+1) = [];
//	end

//		% Resources:
//		% https://www.mathworks.com/matlabcentral/fileexchange/16581-hidden-point-removal
//		% https://www.isprs-ann-photogramm-remote-sens-spatial-inf-sci.net/II-5/9/2014/isprsannals-II-5-9-2014.pdf
//		% http://vecg.cs.ucl.ac.uk/Projects/SmartGeometry/robustPointVisibility/paper_docs/VisibilityOfNoisyPointCloud_small.pdf
//		% http://www.cloudcompare.org/
//
//		p = [X; Y; Z]';                         %NxD D dimensional point cloud.                    
//		k=5;
//		Camera = [-10,k,k];                     %1xD  D dimensional Camera viewpoint.
//		param = 2.3;                            %param - parameter for the algorithm. Indirectly sets the radius.
//		vp = HPR(p,Camera,param);               %visiblePtInds - indices of p that are visible from C.
//		Xvp = X(vp);
//		Yvp = Y(vp);
//		Zvp = Z(vp);
	
	
	List<Integer> HPR(Point3D[] p, Point3D C, double param )
	{
//		p = p-repmat(C,[numPts 1]);												%Move C to the origin  

		int n = p.length;		
		for( int i=0; i<n; i++)
			p[i].set(p[i].getX()-C.getX(), p[i].getY()-C.getY(), p[i].getZ()-C.getZ());
			
//		normp = sqrt(dot(p,p,2));                                         %Calculate ||p||		

		double[] normp = new double[n];
		double maxNormp = 0;
		for(int i=0; i<n; i++)  // sqrt of sum of p(i)*p(i) by rows, there's only one row
		{
			normp[i] = Math.sqrt(p[i].getX()*p[i].getX() + p[i].getY()*p[i].getY() + p[i].getZ()*p[i].getZ() );
			if (normp[i] > maxNormp)
				maxNormp = normp[i];
		}
		
//		R = repmat(max(normp)*(10^param),[numPts 1]);                     %Sphere radius

		double[] R = new double[n];
		for(int i=0; i<n; i++)
			R[i] = maxNormp*Math.pow(10, param);
	
//		P = p+2*repmat(R-normp,[1 dim]).*p./repmat(normp,[1 dim]);        %Spherical flipping

		Point3d[] P = new Point3d[n];
		for(int i=0; i<n; i++)
		{
			double norm = normp[i];
			double r = 2*(R[i] - norm);
			double s = (1+r)/norm;  //s = (1-2*(R[i] - norm))/norm; = 
			P[i] = new Point3d(s*p[i].getX(), s*p[i].getY(), s*p[i].getZ());
		}
		
//		visiblePtInds = unique(convhulln([P;zeros(1,dim)]));              %convex hull

// http://box2d.org/files/GDC2014/DirkGregorius_ImplementingQuickHull.pdf
		
		QuickHull3D qhull = new QuickHull3D();  //https://www.cs.ubc.ca/~lloyd/java/quickhull3d.html
		qhull.build(P);		
		int[] indices = qhull.getVertexPointIndices();

		List<Integer> listOfIndices = new ArrayList<Integer>();
		for(int i=0; i<indices.length; i++)
			listOfIndices.add(indices[i]);
			
		Collections.sort(listOfIndices);
		
		List<Integer> visiblePtInds = new ArrayList<Integer>();
		int lastIndex = -1;
		for(int i=0; i<listOfIndices.size(); i++)
		{
			int index = listOfIndices.get(i);
			if(index != lastIndex)
				visiblePtInds.add(index);
			lastIndex = index;
		}
		
//		visiblePtInds(visiblePtInds==numPts+1) = [];   // ??????????????
		
		return visiblePtInds;
	}
	
	
List<Point3D> hiddenPointRemoval( Point3D[] p, Point3D C, double param )
{
	List<Point3D> list = new ArrayList<Point3D>();
	List<Integer> vp = HPR(p, C, param);
	for(int i=0; i<vp.size(); i++)		
		list.add(p[vp.get(i)]);
	return list;
}
		

	@BeforeEach
	public void setup() throws Exception
	{
		uiIsGoingDown.setFalse();

		if (VISUALIZE)
		{
			SharedMemoryJavaFXMessager jfxMessager = new SharedMemoryJavaFXMessager(PolygonizerVisualizerUI.getMessagerAPI());
			messager = jfxMessager;
			createVisualizer(jfxMessager);
		}
		else
		{
			messager = new SharedMemoryMessager(PolygonizerVisualizerUI.getMessagerAPI());
			messager.startMessager();
			new PolygonizerManager(messager);
		}
	}

	@SuppressWarnings("restriction")
	private void createVisualizer(JavaFXMessager messager)
	{
		AtomicReference<PolygonizerVisualizerUI> ui = new AtomicReference<>(null);

		PlatformImpl.startup(() -> {
			try
			{
				Stage primaryStage = new Stage();
				primaryStage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, event -> uiIsGoingDown.setTrue());

				ui.set(new PolygonizerVisualizerUI(messager, primaryStage));
				ui.get().show();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		});

		while (ui.get() == null)
			ThreadTools.sleep(200);
	}

	@AfterEach
	public void tearDown()
	{
		if (VISUALIZE)
		{
			while (!uiIsGoingDown.booleanValue())
				ThreadTools.sleep(100);
		}
	}

	public static void main(String[] args)
	{
		MutationTestFacilitator.facilitateMutationTestForClass(ConcaveHullTestBasics.class, ConcaveHullTestBasics.class);

		ConcaveHullTestBasics basics = new ConcaveHullTestBasics();
		basics.createSombrero(-5.0, -5.0, 51);
	}

}
