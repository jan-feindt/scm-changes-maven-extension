scm-changes-maven-extension
===========================

Fork of scm-changes-maven-extension provided by Dan Fabulich.

See: https://svn.apache.org/repos/asf/maven/sandbox/trunk/shared/scm-changes-maven-extension/
and https://mail-archives.apache.org/mod_mbox/maven-dev/201011.mbox/%3Calpine.OSX.2.00.1011051125410.368@dfab-2.local%3E

The extension builds only modules of a multi-module maven project containing files that you personally have changed (according to SCM), and projects that depend on those projects (downstream).

To use, add this to your parent POM:

    <build>
       <extensions>
        <extension>
          <groupId>org.apache.maven.shared</groupId>
          <artifactId>scm-changes-maven-extension</artifactId>
          <version>1.0-SNAPSHOT</version>
        </extension>
      </extensions>
    </build>

Be sure to also specify an SCM connection:

    <scm>
      <connection>scm:svn:http://svn.apache.org/repos/asf/maven/plugins/trunk/maven-reactor-plugin/</connection>
      <developerConnection>scm:svn:https://svn.apache.org/repos/asf/maven/plugins/trunk/maven-reactor-plugin/</developerConnection>
      <url>http://svn.apache.org/viewvc/maven/plugins/trunk/maven-reactor-plugin/</url>
    </scm>

Then run your build like this:

    mvn install -Dmake.scmChanges

That will build only those projects that you changed, and projects that depend on those projects (downstream).

IF IT DOESN'T APPEAR TO BE WORKING:  Try running mvn with -X to get debug logs.

Note that if you modify the root POM (to add this extension) without checking it in, then EVERYTHING is downstream of
the root POM, so -Dmake.scmChanges will cause a full rebuild; it will appear as if it's not working. You can use
-Dmake.ignoreRootPom to ignore changes in the root POM while testing this extension.

To run on CI define the extension as before. You have to use the extension with additional scripts like this for subversion:

    svn diff -r BASE:HEAD | grep ^Index: | sed 's/^Index: //' >> .scm-updates
    svn update
    mvn install -Dmake.scmUpdates
    rm .scm-updates

That will build only those projects that have changes in SCM, and projects that depend on those projects (downstream).
