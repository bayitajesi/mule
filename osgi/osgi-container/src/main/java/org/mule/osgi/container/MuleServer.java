/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.osgi.container;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.framework.wiring.FrameworkWiring;

public class MuleServer
{

    public static final boolean SHOW_BUNDLE_STATUSES = isShowBundleStatuses();
    public static final boolean SHOW_BUNDLE_DEPENDENCIES = isShowBundleDependencies();

    //public static final String DEFAULT_BASE_FOLDER = "/Users/pablokraan/devel/osgiDev/";
    private static Framework framework = null;

    public static void main(String[] args) throws Exception
    {
        // Register a shutdown hook to make sure the framework is
        // cleanly shutdown when the VM exits.
        registerShutDownHook();

        try
        {
            FrameworkFactory factory = getFrameworkFactory();

            Map<String, String> configProperties = new HashMap<>();
            configProperties.put("org.osgi.framework.bsnversion", "multiple");
            configProperties.put("org.osgi.framework.system.packages.extra", "sun.misc");

            framework = factory.newFramework(configProperties);

            // Initialize the framework, but don't start it yet.
            framework.init();
            //framework.start();

            //FrameworkStartLevel sl = framework.adapt(FrameworkStartLevel.class);
            //sl.setInitialBundleStartLevel(1);

            BundleContext context = framework.getBundleContext();
            //MuleOsgiListener listener = new MuleOsgiListener();
            //context.addFrameworkListener(listener);


            final List<File> mavenRepos = new ArrayList<>();
            mavenRepos.add(new File("/Users/pablokraan/.m2/repository"));

            final List<BundleInfo> bundles = new ArrayList<>();

            bundles.add(new BundleInfo("mvn:com.lmax/disruptor/3.3.0/jar", 30));
            bundles.add(new BundleInfo("mvn:org.apache.logging.log4j/log4j-api/2.2/jar", 30));
            bundles.add(new BundleInfo("mvn:org.apache.logging.log4j/log4j-core/2.2/jar", 30));
            bundles.add(new BundleInfo("mvn:org.apache.logging.log4j/log4j-1.2-api/2.2/jar", 30));
            bundles.add(new BundleInfo("mvn:org.apache.logging.log4j/log4j-slf4j-impl/2.2/jar", 30));
            bundles.add(new BundleInfo("mvn:org.slf4j/slf4j-api/1.7.7/jar", 25));
            bundles.add(new BundleInfo("mvn:org.slf4j/jcl-over-slf4j/1.7.7/jar", 30));
            bundles.add(new BundleInfo("mvn:commons-logging/commons-logging/1.2/jar", 30));
            bundles.add(new BundleInfo("mvn:org.apache.logging.log4j/log4j-jcl/2.2/jar", 30));
            bundles.add(new BundleInfo("mvn:org.apache.logging.log4j/log4j-jul/2.2/jar", 30));

            bundles.add(new BundleInfo("mvn:org.ops4j.pax.url/pax-url-aether/2.4.1", 30));
            bundles.add(new BundleInfo("mvn:org.ops4j.pax.url/pax-url-wrap/2.3.0/jar/uber", 30));
            installAndStartBundles(new SimpleMavenResolver(mavenRepos), context, bundles);

            //setStartLevel(100);
            framework.start();

            installFeature(context, new MuleCoreFeature());

            FrameworkEvent event;
            do
            {
                // Start the framework.
                if (SHOW_BUNDLE_STATUSES)
                {
                    showBundleStatuses(context);
                }
                if (SHOW_BUNDLE_DEPENDENCIES)
                {
                    showDependencies(context);
                }

                // Wait for framework to stop to exit the VM.
                event = framework.waitForStop(0);
            }

            // If the framework was updated, then restart it.
            while (event.getType() == FrameworkEvent.STOPPED_UPDATE);

            // Otherwise, exit.
            System.exit(0);
        }
        catch (Exception ex)
        {
            System.err.println("Could not create framework: " + ex);
            ex.printStackTrace();
            System.exit(0);
        }
    }

    protected static void setStartLevel(int level) {
        framework.adapt(FrameworkStartLevel.class).setStartLevel(level);
    }

    private static void installAndStartBundles(SimpleMavenResolver resolver, BundleContext context, List<BundleInfo> bundles) {
        for (BundleInfo bundleInfo : bundles) {
            try {
                Bundle b;
                //if (bundleInfo.uri.toString().startsWith("reference:file:")) {
                //    URI temp = URI.create(bundleInfo.uri.toString().substring("reference:file:".length()));
                //    URI resolvedURI = resolver.resolve(temp);
                //    URI finalUri = URI.create("reference:file:" + config.karafBase.toURI().relativize(resolvedURI));
                //    b = context.installBundle(finalUri.toString());
                //} else {
                    URI resolvedURI = resolver.resolve(new URI(bundleInfo.getLocation()));
                    b = context.installBundle(bundleInfo.getLocation(), resolvedURI.toURL().openStream());
                //}
                //b.adapt(BundleStartLevel.class).setStartLevel(bundleInfo.getStartLevel());
                if (!isFragment(b)) {
                    b.start();
                }
            } catch (Exception  e) {
                throw new RuntimeException("Error installing bundle with url: " + bundleInfo.getLocation() + " and startlevel: " + bundleInfo.getStartLevel(), e);
            }
        }
    }

    private static List<Bundle> installFeature(BundleContext context, FeatureInfo featureInfo)
    {
        System.out.println("Installing feature: " + featureInfo.getName());
        List<Bundle> bundles = new ArrayList<>();

        for (Dependency dependency : featureInfo.getDependencies())
        {
            try
            {
                final Bundle bundle = context.installBundle(dependency.getLocation());

                // If we have found and/or successfully installed a bundle,
                // then add it to the list of bundles to potentially start
                // and also set its start level accordingly.
                if (bundle != null && !isFragment(bundle))
                {
                    bundles.add(bundle);
                    //bundle.adapt(BundleStartLevel.class).setStartLevel(dependency.getStartLevel());

                    bundle.start();
                }
            }
            catch (BundleException ex)
            {
                System.err.println("Auto-deploy install error on bundle " + dependency.getLocation() + " - "
                                   + ex + ((ex.getCause() != null) ? " - " + ex.getCause() : ""));
            }
        }

        return bundles;
    }

    private static void registerShutDownHook()
    {
        Runtime.getRuntime().addShutdownHook(new Thread("Mule Launcher Shutdown Hook")
        {
            public void run()
            {
                try
                {
                    if (framework != null)
                    {
                        framework.stop();
                        framework.waitForStop(0);
                    }
                }
                catch (Exception ex)
                {
                    System.err.println("Error stopping framework: " + ex);
                }
            }
        });
    }

    private static void showDependencies(BundleContext context)
    {
        System.out.println("\nBUNDLE DEPENDENCIES:");
        FrameworkWiring frameworkWiring = context.getBundle().adapt(FrameworkWiring.class);
        for (Bundle bundle : frameworkWiring.getBundle().getBundleContext().getBundles())
        {
            System.out.println("Dependency closure for bundle: " + bundle.getSymbolicName());
            Collection<Bundle> dependencyClosure = frameworkWiring.getDependencyClosure(Collections.singleton(bundle));
            for (Bundle dependency : dependencyClosure)
            {
                System.out.println("Bundle: " + dependency.getSymbolicName());
            }
        }
    }

    private static void showBundleStatuses(BundleContext context)
    {
        System.out.println("\nBUNDLE STATUS:");

        for (Bundle bundle : context.getBundles())
        {
            System.out.println("Bundle " + bundle.getSymbolicName() + " is in state: " + getBundleState(bundle.getState()));
        }
    }

    private static String getBundleState(int state)
    {
        switch (state)
        {
            case Bundle.INSTALLED:
                return "INSTALLED";
            case Bundle.RESOLVED:
                return "RESOLVED";
            case Bundle.ACTIVE:
                return "ACTIVE";
            case Bundle.UNINSTALLED:
                return "UNINSTALLED";
            case Bundle.STARTING:
                return "STARTING";
            case Bundle.STOPPING:
                return "STOPPING";
            default:
                throw new IllegalStateException("Unknown bundle state: " + state);
        }
    }

    /**
     * Simple method to parse META-INF/services file for framework factory.
     * Currently, it assumes the first non-commented line is the class name
     * of the framework factory implementation.
     *
     * @return The created <tt>FrameworkFactory</tt> instance.
     * @throws Exception if any errors occur.
     */
    private static FrameworkFactory getFrameworkFactory() throws Exception
    {
        URL url = MuleServer.class.getClassLoader().getResource(
                "META-INF/services/org.osgi.framework.launch.FrameworkFactory");
        if (url != null)
        {
            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            try
            {
                for (String s = br.readLine(); s != null; s = br.readLine())
                {
                    s = s.trim();
                    // Try to load first non-empty, non-commented line.
                    if ((s.length() > 0) && (s.charAt(0) != '#'))
                    {
                        return (FrameworkFactory) Class.forName(s).newInstance();
                    }
                }
            }
            finally
            {
                if (br != null)
                {
                    br.close();
                }
            }
        }

        throw new Exception("Could not find framework factory.");
    }

    private static boolean isFragment(Bundle bundle)
    {
        return bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null;
    }

    private static boolean isShowBundleStatuses()
    {
        String value = System.getProperty("mule.osgi.showBundleStatuses", "true");

        return Boolean.valueOf(value);
    }

    private static boolean isShowBundleDependencies()
    {
        String value = System.getProperty("mule.osgi.showBundleDependencies", "true");

        return Boolean.valueOf(value);
    }

}
