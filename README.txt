The repository hosts BinoBank, the platform for collecting taxonomic name strings.
BinoBank is developed by David King and Guido Sautter on behalf of The Open University
and Karlsruhe Institute of Technology (KIT) under the ViBRANT project (EU Grant
FP7/2007-2013, Virtual Biodiversity Research and Access Network for Taxonomy).

Copyright (C) 2011-2013 ViBRANT (FP7/2007-2013, GA 261532), by D. King & G. Sautter

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program (LICENSE.txt); if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.



SYSTEM REQUIREMENTS

Java Runtime Environment 1.5 or higher, Sun/Oracle JRE recommended

Apache Tomcat 5.5 or higher (other servlet containers should work as well, but have not been tested yet)
If you are running Tomcat with a Server JRE 1.7 or higher, you have to enable Java 1.6 compatibility mode, as otherwise some required classes are excluded from the class path.
This works as follows:
- Linux/Unix: in /etc/init.d/tomcat, add the parameter "-Djava.specification.version=1.6" to the JAVA_OPTS="..."; line
- Windows: include the parameter "-Djava.specification.version=1.6" wherever you set other parameters like the maximum memory as well

A database server, e.g. PostgreSQL (drivers included for version 8.2) or Microsoft SQL Server (drivers included)
Instead, you can also use Apache Derby embedded database (included)
(using Apache Derby is the default configuration, so you can test BinoBank without setting up a database)



DEPENDENCIES

BinoBank builds on other open source projects; the JAR files these projects build into are included in the "lib" folder for convenience.
In addition, the Ant build script checks if these projects are present in your workspace, and if so, uses the recent builds found in their "dist" folders.

- idaho-core (http://code.google.com/p/idaho-core/)

- idaho-extensions (http://code.google.com/p/idaho-extensions/)

- openstringpool (http://git.scratchpads.eu/v/openstringpool.git)

- java-uuid-generator (http://github.com/cowtowncoder/java-uuid-generator/)



SETTING UP A BinoBank NODE

Download BinoBank.zip into Tomcat's webapps folder (an exploded archive directory, zipped up for your convenience; WAR deployment is impractical, as updates would overwrite the configurations you make)
Instead, you can also check out the project from GIT, build the ZIP file using Ant, and then deploy BinoBank.zip to your Tomcat

Create a BinoBank sub folder in Tomcat's webapps folder.

Un-zip the exploded archive directory into the BinoBank folder.
If you have WebAppUpdater (builds with idaho-core) installed, you can also simply type "bash update BinoBank" in the console.

Put BinoBank.zip into the BinoBank folder for others to download.

Now, it's time for some configuration:

    To enable the BinoBank node to store parsed taxonomic names in the file system and connect to other BinoBank nodes, give the web application the permission to create and manipulate files and folders within its deployment folder and to establish outgoing network connections (there are two ways to achieve this):

        The simple, but very coarse way is to disable Tomcat's security manager altogether (not recommended)

        More finegrained way is to add the permission statement below to Tomcat's security configuration (recommended); the security configuration resides inside Tomcat's conf folder, which is located on the same level as the webapps folder; the actual configuration file to add the permission to is either catalina.policy directly in the conf folder, or 04webapps.policy in the conf/policy.d folder, whichever is present; if both files are present, either will do:

        grant codeBase "file:${catalina.base}/webapps/BinoBank/WEB-INF/lib/-" {
        	permission java.net.SocketPermission "*.*", "connect";
        	permission java.io.FilePermission "WEB-INF/-", "read,write,delete,execute";
        }

    Adjust the config.cnfg files in the WEB-INF/<xyz>Data folders:

        In the config file in WEB-INF/bbkData/, enter a (presumably) globally unique BinoBank domain name, which should identify the institution the BinoBank node runs in and, if the institution runs multiple BinoBank nodes, also distinguish the node being set up from the other ones already running (if you set up the node for testing or experimentation purposes, please choose a domain name ending in "-Test", ".test", "-Dev", "-Development", ".dev", or something similar)

        In the same file, enter the preferred access URL for the node, i.e., the (preferred) URL for accessing the node from the WWW

        In the same file, enter the administration passcode for the BinoBank node being set up

        To secure taxonomic name upload with ReCAPTCHA to avoid spamming, obtain a ReCAPTCHA API key pair and put it in the config file in the WEB-INF/uploadData/ folder

    If not using an embedded database, create a database for BinoBank in your database server, e.g. BinoBankDB

    Adjust the web.cnfg file in the WEB-INF folder:

        Adjust the JDBC settings to access the database created for BinoBank
        (by default configured to use Apache Derby in embedded mode)

        Set the stringPoolNodeName setting to the name assigned to the BinoBank servlet in the web.xml (which is BinoBank if you did not change it) so dependent local servlets can connect to it directly (Java method invocations) instead of the local network loopback adapter for better performance
        (if you do not change the web.xml file, you need not change this setting, either)

        Set the stringPoolNodeUrl setting to the access URL you configured above, or to a localhost URL; in any case, the URL used should point to the BinoBank servlet directly for better performance, even if the preferred external access URL is one proxied through a local Apache web server or the like
        (the default setting assumes Tomcat running on port 8080, you need to change this only if your Tomcat is running on a different port)

To make your BinoBank node credit your institution, do the following:

    Put your own institution logo in the images folder

    Customize the files footer.html and popupFooter.html in the WEB-INF folder to include your institution name and logo by replacing
    yourLogo.gif with the name of your logo image file,
    yourUrl.org with the link to your institution,
    Your Institution with the name of your institution,
    YourInstitutionAcronym with the acronym of your institution

Put the WAR file you downloaded into the webapps/BinoBank/ folder so others can download it



LINKING THE BinoBank NODE TO THE NETWORK

Access the web application through a browser (the search form should show up)

Follow the Administer This Node link at the bottom of the page

Enter the passcode configured above to access the administration page

Enter the access URL of another BinoBank node (maybe the one the zip file was downloaded from, simply by replacing the BinoBank.zip file name with bbk, resulting in http://<binoBankHostDownloadedFromIncludingPort>/BinoBank/bbk, for instance) into the Connect to other Nodes form and click the Add Node button
==> A list of other nodes shows up, labeled Connected Nodes

Configure replication of data in the Connected Nodes table and click the Update Nodes button to submit it
==> afterwards, the web application might be busy for a while importing the references from the other nodes via the replication mechanism



CUSTOMIZING THE LAYOUT

The servlets generate the search and upload forms as well as the search results and reference detail views dynamically from multiple files residing in the WEB-INF folder or one of its sub folders:

binoBank.html is the template for the main pages

The bodies of header.html, navigation.html, and footer.html are inserted in the template where the <includeFile file="<filename>.html"> tags are in the template

The CSS styles for all these files are in binoBank.css

binoBankPopup.html is the template for the reference detail views

The bodies of popupHeader.html, popupNavigation.html, and popupFooter.html are inserted in the template where the <includeFile file="<filename>.html"> tags are in the template

The CSS styles for all these files are also in binoBank.css

The search or upload form is inserted where the <includeForm/> tag is in the template

The search or upload result is inserted where the <includeResult/> tag is in the template

    The search form content comes from WEB-INF/searchData/searchFields.html; the actual form tag is created by the servlet

    The CSS styles for the search form and results are in WEB-INF/searchData/binoBankSearch.css

    The upload form comes from WEB-INF/searchData/uploadFields.html; the actual form tag is created by the servlet

    The reCAPTCHA widget is inserted where the <includeReCAPTCHA/> tag is in uploadFields.html

    The CSS styles for the upload form and results are in WEB-INF/uploadData/binoBankUpload.css

onnNodeAdminPage.html is the template for the administration page

The respective CSS styles are in onnNodeAdminPage.css

To customize general page layout, change the binoBank.html and binoBankPopup.html files and the respective stylesheets

    This can be as simple customizing respective CSS styles (can be tested on statically saved post-generation HTML pages)

    This can include changing the file names or the placement of the <includeFile .../> tags; when changing the file names or adding <includeFile .../> tags, make sure that the references files exist (requires the web application to run for testing)

    This can include changing the placement of the <includeForm/> and <includeResult/> tags; make sure, however, that these tags remain in the template page, as otherwise the functional parts of the pages cannot be inserted (requires the web application to run for testing)

To customize page header, navigation, or footer, customize the respective HTML files and the respective stylesheets

    This can be as simple customizing respective CSS styles (can be tested on statically saved post-generation HTML pages)

    This can include adding new <includeFile .../> tags; when doing this, make sure that the references files exist (requires the web application to run for testing)

Do not add header, navigation, or footer content to the binoBank.html or binoBankPopup.html files directly, but use the respective inserted files instead (requires the web application to run for testing)

If you alter any other files, include them in the update.cnfg file (with full path, starting with WEB-INF) so they are not replaced in case of an update.