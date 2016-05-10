package com.antiy.weiwei.fakesign;

import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by weiwei on 16-3-14.
 */
public class FakeSign implements IXposedHookZygoteInit, IXposedHookLoadPackage{

    private static final String ANDROID_MANIFEST_FILENAME = "AndroidManifest.xml";

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {

    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {

        // XposedBridge.log(loadPackageParam.packageName);
//        String SDPath = "/sdcard/";
        String SDPath = "/data/local/tmp/";
        if(!new File(SDPath + loadPackageParam.packageName + ".apk").exists()){
            return;
        }

        XposedBridge.log("FakeSig " + loadPackageParam.packageName);

        Signature signature = getAPKSignatures(SDPath + loadPackageParam.packageName + ".apk");

        FakeSigHook fakeSigHook = new FakeSigHook(signature);

        XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager",
                loadPackageParam.classLoader,
                "getPackageInfo",
                String.class,
                int.class,
                fakeSigHook);
    }

    class FakeSigHook extends XC_MethodHook {
        private Signature mSignature;

        public FakeSigHook(Signature signature) {
            mSignature = signature;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            PackageInfo resultPackageInfo = (PackageInfo) param.getResult();
            if(resultPackageInfo == null)
                return;
            resultPackageInfo.signatures[0] = mSignature;
            param.setResult(resultPackageInfo);
            Log.d("WQ_FakeSig", "faked", new Exception("WQ_FakeSig"));
        }
    }

    private Signature getAPKSignatures(String apkPath) {
        String PATH_PackageParser = "android.content.pm.PackageParser";
        try {
            Class pkgParserCls = Class.forName(PATH_PackageParser);
            Class[] typeArgs = new Class[1];
            typeArgs[0] = String.class;
            Constructor pkgParserCt = pkgParserCls.getConstructor(typeArgs);
            Object[] valueArgs = new Object[1];
            valueArgs[0] = apkPath;
            Object pkgParser = pkgParserCt.newInstance(valueArgs);
            Log.d(apkPath, "pkgParser:" + pkgParser.toString());
            // 这个是与显示有关的, 里面涉及到一些像素显示等等, 我们使用默认的情况
            DisplayMetrics metrics = new DisplayMetrics();
            metrics.setToDefaults();
            // PackageParser.Package mPkgInfo = packageParser.parsePackage(new
            // File(apkPath), apkPath,
            // metrics, 0);
            typeArgs = new Class[4];
            typeArgs[0] = File.class;
            typeArgs[1] = String.class;
            typeArgs[2] = DisplayMetrics.class;
            typeArgs[3] = Integer.TYPE;
            Method pkgParser_parsePackageMtd = pkgParserCls.getDeclaredMethod("parsePackage",
                    typeArgs);
            valueArgs = new Object[4];
            valueArgs[0] = new File(apkPath);
            valueArgs[1] = apkPath;
            valueArgs[2] = metrics;
            valueArgs[3] = PackageManager.GET_SIGNATURES;
            Object pkgParserPkg = pkgParser_parsePackageMtd.invoke(pkgParser, valueArgs);

            typeArgs = new Class[2];
            typeArgs[0] = pkgParserPkg.getClass();
            typeArgs[1] = Integer.TYPE;
            Method pkgParser_collectCertificatesMtd = pkgParserCls.getDeclaredMethod("collectCertificates",
                    typeArgs);
            valueArgs = new Object[2];
            valueArgs[0] = pkgParserPkg;
            valueArgs[1] = PackageManager.GET_SIGNATURES;
            pkgParser_collectCertificatesMtd.invoke(pkgParser, valueArgs);
            // 应用程序信息包, 这个公开的, 不过有些函数, 变量没公开
            Field packageInfoFld = pkgParserPkg.getClass().getDeclaredField("mSignatures");
            Signature[] info = (Signature[]) packageInfoFld.get(pkgParserPkg);
//            MediaApplication.logD(DownloadApk.class, "size:"+info.length);
//            MediaApplication.logD(DownloadApk.class, info[0].toCharsString());
            return info[0];
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
