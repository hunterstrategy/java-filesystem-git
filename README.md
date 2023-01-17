# jnfsgit - Java NIO FileSystem: git

This is a FileSystem provider for Java 7+ `nio` (new IO) that allows browsing
git trees at specified revisions through a standard Java API.

To use, build a URI that points to a git filesystem:

`git:///path/to/repository#revspec`

The `revspec` matches a standard git
[rev-parse](https://git-scm.com/book/en/v2/Git-Tools-Revision-Selection) for a
single revision.

For example, `git:///var/work/repo#HEAD`

The underlying filesystem is *read only*.

You can also go directly to a file without walking the filesystem:

`git:///path/to/repository?/path/to/file#revspec`

Example:

`git:///var/work/repo?/pom.xml#1.1.0-RELEASE`

You can also use the `GitURI` class' `toURI` functions to help build URIs.

This can work with normal or bare repositories. File contents are read from
the git object store and not from the working tree local filesystem.

## Sample

Print all file names in a repository, where the `main` branch ref currently points.

```java
try (FileSystem fs = FileSystems.newFileSystem(GitURI.toURI("/path/to/repo", "main"))) {
  Files.walk(fs.getPath("/")).forEach(System.out::println);
}
```

## Environment Configuration

### Blob storage IO

When reading blobs from the backing pointer, jnfsgit uses a temporary `byte[]`
to transfer between its source `ByteBuffer` and the destination buffer. If you
are doing a lot of IO, this may cause unwanted memory pressure. This
implementation was taken in part because this library currently restricts itself
to Java 11 features, so the `ByteBuffer#slice(int, int)` operation isn't available.

You can provide your own implementation, for example using `slice`, or a pool of
`byte[]` to reduce memory pressure, by setting the environment variable
`JNFSGIT_IO_WRAPPER` to a fully-qualified class name.

This class must be one of two types:

1. A `java.util.function.Function` that takes `ByteBuffer` as an argument and
   returns `SeekableByteChannel`.
2. A `SeekableByteChannel` with a public constructor that takes a `ByteBuffer`
   as its sole argument.

# libgit2 bindings

*Warning: Binding API is unstable.*

This project also contains prototype libgit2 bindings. Most other languages have
good libgit2 bindings, so familiarity with this library is portable to many
ecosystems.

However, Java only has 2 binding projects available (as far as I can tell):

* jagged - Abandoned several years ago, has build/packaging issues (according to its README)
* git24j - Claims to be under active development (doesn't appear that way)

Both projects use JNI so they have extra build steps and packaging requirements.

The bindings in this project use JNR-FFI, an annotation-based binding system
that is almost as fast as JNI but requires no custom C/C++ library to be
maintained alongside the Java code. No extra build steps are required - just
depend on this artifact as you would expect like a native Java library.

Why not JGit? Mainly because I'm already familiar with libgit2 from other ecosystems
and I've had success with it. Second, despite JGit's age, I found the documentation
to be sparse. JGit, being used in Eclipse and by Gerrit, is a robust library. But
I decided instead that I wanted to use libgit2 as I have before.

## Future

This binding should be in its own proper repository, but for the time being, I
am mostly only implementing what I need for jnfsgit. If there's some interest in
these bindings, we can move them to their own repo, put some more attention on
the API, and complete the wrapping of libgit2 function calls (it's big).

# Roadmap

## JNFSGIT

- [ ] Allow Repository to be passed in as environment to re-use resource?
- [ ] Optional attribute type to use revwalk to get more accurate creation / lastModified times
- [ ] Supporting git submodules when walking tree?
- [ ] Java 11 module declaration?

## libgit2 bindings

- [ ] Dedicated repository and name (jnr-libgit2)?
- [ ] Revwalk API to find/filter revisions
- [ ] Tags/Annotated tags
- [ ] Notes?
- [ ] Commit message trailers (Signed-Off)
- [ ] Ensure current memory management strategy isn't duplicating effort with JNR memory management utils
