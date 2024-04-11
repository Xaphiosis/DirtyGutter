# About

Line changed/added/deleted indicators in the gutter for JEdit, extended with
xsymbol subtitution when a file is read in the `UTF-8-Isabelle` locale.

The original version would flag all lines with xsymbols as changed, since
`\<lambda>` on disk would show up as `λ` in the buffer.

The xsymbol table was exported from Isabelle 2023.

If you want the diff to be against the current git HEAD, you also need an
updated `jedit-dirty-gutter-plugin`
(e.g. `jedit-git-dirty-gutter-plugin-0.1.1-xsymbol-all.jar`)

This repository was forked from:
https://git.code.sf.net/p/jedit/DirtyGutter

# Compiling

There are probably canonical ways to do this, but a hacky way to get the jar
goes like this:

```
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ant -Djedit.install.dir=../../jedit/build -Dbuild.support=../build-support -Djedit.plugins.dir=../../jedit-plugins dist
```

You must point the relevant paths above to the resources needed:
- `jedit.install.dir` is where to find `jedit.jar`
- `build.support` is where you put the build support repo, obtainable from `https://jedit.svn.sourceforge.net/svnroot/jedit/build-support/trunk`
- `jedit.plugins.dir` is where other jars are stored that this plugin needs to compile, which in this case is `CommonControls.jar` (1.7.4 worked)

