package com.plugin.buildplugin;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.ListBoxModel;
import java.io.BufferedReader;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.bind.JavaScriptMethod;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link HelloWorldBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform} method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class PluginBuilder extends Builder{

    private final String getOffer;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public PluginBuilder(String getOffer) {
        this.getOffer = getOffer;
    }

    /**
     * We'll use this from the {@code config.jelly}.
     */
    public String getgetOffer() {
        return getOffer;
    }
    

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException{
        // This is where you 'build' the project.
        // Since this is a dummy, we just say 'hello world' and call that a build.

        // This also shows how you can consult the global configuration of the builder
        
        listener.getLogger().println("Selected Offer:"+ getOffer);
        EnvVars envVars = new EnvVars();
        envVars = build.getEnvironment(listener);
        envVars.put("offer", getOffer);
        listener.getLogger().println("Environment Variables are: "+envVars.toString());
        return true;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link HelloWorldBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See {@code src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly}
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use {@code transient}.
         */
        public HashMap<String, HashMap<String,String>> offers;
        String respCode;
        private int lastEditorId = 0;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        
        @JavaScriptMethod
        public synchronized String createEditorId() {
            return String.valueOf(lastEditorId++);
        }
        
        @JavaScriptMethod
        public HashMap<String, HashMap<String,String>> fetchOffers(){
        System.out.println("Inside fetchoffers!!");
        System.out.println("Before UI:"+offers);
        return offers;
    }
        public FormValidation dogetAmazonOffers(){
            this.getAmazonOffers();
            return FormValidation.ok();
        }
        public void getAmazonOffers(){
            
            try {
            HttpClient client = HttpClientBuilder.create().build();
            String url = "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/index.json";
            HttpGet request = new HttpGet(url);
            System.out.println("\nSending 'GET' request to URL : " + url);
            HttpResponse response = client.execute(request);
            respCode = String.valueOf(response.getStatusLine().getStatusCode());
            System.out.println("Response Code :"+response.getStatusLine().getStatusCode());
            StringBuilder result;
            try (BufferedReader rd = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()))) {
                result = new StringBuilder();
                String line;
                while ((line = rd.readLine()) != null) {
                    result.append(line);
                }
                JSONParser parser = new JSONParser();
                Object resultObject = parser.parse(result.toString());
                org.json.simple.JSONObject received_data = (org.json.simple.JSONObject) resultObject;
                if(resultObject instanceof HashMap && respCode.equals("200"))
                {
                    Object offersObj = received_data.get("offers");
                    offers = (HashMap) offersObj;                    
                }
            }catch (ParseException ex) {
                Logger.getLogger(PluginBuilder.class.getName()).log(Level.SEVERE, null, ex);
            }
            }catch (IOException ex) {
            System.out.println("IOException:"+ ex.getMessage());
            }
            
        }
        
        public ListBoxModel doFillGetOfferItems(@QueryParameter("getOffer") String getOffer){
            System.out.println("inside dofill offers:"+ getOffer);
            return new ListBoxModel(new ListBoxModel.Option(getOffer));
        }
        
        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a name");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the name too short?");
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return "Get REST Data";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            req.bindJSON(this, formData);
            save();
            return super.configure(req,formData);
        }

        
    }
}

