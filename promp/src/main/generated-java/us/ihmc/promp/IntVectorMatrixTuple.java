// Targeted by JavaCPP version 1.5.7: DO NOT EDIT THIS FILE

package us.ihmc.promp;

import java.nio.*;
import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.annotation.*;

import static us.ihmc.promp.global.promp.*;

@NoOffset @Name("std::tuple<int,Eigen::VectorXd,Eigen::MatrixXd>") @Properties(inherit = us.ihmc.promp.presets.ProMPInfoMapper.class)
public class IntVectorMatrixTuple extends Pointer {
    static { Loader.load(); }
    /** Pointer cast constructor. Invokes {@link Pointer#Pointer(Pointer)}. */
    public IntVectorMatrixTuple(Pointer p) { super(p); }
    public IntVectorMatrixTuple(int value0, @ByRef EigenVectorXd value1, @ByRef EigenMatrixXd value2) { allocate(value0, value1, value2); }
    private native void allocate(int value0, @ByRef EigenVectorXd value1, @ByRef EigenMatrixXd value2);
    public IntVectorMatrixTuple()       { allocate();  }
    private native void allocate();
    public native @Name("operator =") @ByRef IntVectorMatrixTuple put(@ByRef IntVectorMatrixTuple x);

    public int get0() { return get0(this); }
    @Namespace @Name("std::get<0>") public static native int get0(@ByRef IntVectorMatrixTuple container);
    public @ByRef EigenVectorXd get1() { return get1(this); }
    @Namespace @Name("std::get<1>") public static native @ByRef EigenVectorXd get1(@ByRef IntVectorMatrixTuple container);
    public @ByRef EigenMatrixXd get2() { return get2(this); }
    @Namespace @Name("std::get<2>") public static native @ByRef EigenMatrixXd get2(@ByRef IntVectorMatrixTuple container);
}

