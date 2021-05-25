# Tor upgrade in Bisq

This guide describes the steps necessary to upgrade the tor dependencies used by Bisq.

## Background

Bisq uses two libraries for tor: [netlayer][1] and [tor-binary][2].

As per the project's authors, `netlayer` is _"essentially a wrapper around the official Tor releases, pre-packaged for
easy use and convenient integration into Kotlin/Java projects"_.

Similarly, `tor-binary` is _"[the] Tor binary packaged in a way that can be used for java projects"_. The project
unpacks the tor browser binaries to extract and repackage the tor binaries themselves.

Therefore, upgrading tor in Bisq comes down to upgrading these two artefacts.


## Upgrade steps


### 1. Decide if upgrade necessary

 - Find out which tor version Bisq currently uses
   - Find out the current `netlayer` version (see `netlayerVersion` in `bisq/build.gradle`)
   - Find that release on the project's [releases page][3]
   - The release description says which tor version it includes
- Find out the latest available tor release
   - See the [official tor changelog][4]


### 2. Update `tor-binary`

During this update, you will need to keep track of:

 - the new tor browser version
 - the new tor binary version

Create a PR for the `master` branch of [tor-binary][2] with the following changes:

 - Decide which tor browser version contains the desired tor binary version
   - The official tor browser releases are here: https://dist.torproject.org/torbrowser/
 - For the chosen tor browser version, get the list of SHA256 checksums and its signature
   - For example, for tor browser 10.0.12:
     - https://dist.torproject.org/torbrowser/10.0.12/sha256sums-signed-build.txt
     - https://dist.torproject.org/torbrowser/10.0.12/sha256sums-signed-build.txt.asc
 - Verify the signature of the checksums list (see [instructions][5])
 - Update the `tor-binary` checksums
   - For each file present in `tor-binary/tor-binary-resources/checksums`:
     - Rename the file such that it reflects the new tor browser version, but preserves the naming scheme
     - Update the contents of the file with the corresponding SHA256 checksum from the list
 - Update `torbrowser.version` to the new tor browser version in:
   - `tor-binary/build.xml`
   - `tor-binary/pom.xml`
 - Update `version` to the new tor binary version in:
   - `tor-binary/pom.xml`
   - `tor-binary-geoip/pom.xml`
   - `tor-binary-linux32/pom.xml`
   - `tor-binary-linux64/pom.xml`
   - `tor-binary-macos/pom.xml`
   - `tor-binary-windows/pom.xml`
   - `tor-binary-resources/pom.xml`
 - Run `mvn install`
   - If it completes successfully, then the artefact is correctly configured


Only the files listed above should be part of the PR. The last step will generate a few extra artefacts (for
example in `tor-binary-resources/src/main/resources`), but these should NOT be committed.

Once the PR is merged, make a note of the commit ID in the `master` branch (for example `a4b868a`), as it will be needed
next.


### 3. Update `netlayer`

Create a PR for the `externaltor` branch of [netlayer][1] with the following changes:

 - In `netlayer/pom.xml`:
   - Update `tor-binary.version` to the `tor-binary` commit ID from above (e.g. `a4b868a`)
 - Bump `version`, representing the `netlayer` artefact version, in:
   - `netlayer/pom.xml`
   - `netlayer/tor/pom.xml`
   - `netlayer/tor.external/pom.xml`
   - `netlayer/tor.native/pom.xml`

Once the PR is merged, make a note of the commit ID in the `externaltor` branch (for example `32779ac`), as it will be
needed next.

Create a tag for the new artefact version, having the new tor binary version as description, for example:

```
# Create tag locally for new netlayer release, on the externaltor branch
git tag -s 0.7.0 -m"tor 0.4.5.6"

# Push it to netlayer repo
git push origin 0.7.0
```


### 4. Update dependency in Bisq

Create a Bisq PR with the following changes:

 - In `bisq/build.gradle` update `netlayerVersion` to the `netlayer` commit ID from above
 - Update the gradle dependency checksums in `bisq/gradle/verification-metadata.xml`




## Credits

Thanks to freimair, JesusMcCloud, mrosseel, sschuberth and cedricwalter for their work on the original
[tor-binary](https://github.com/JesusMcCloud/tor-binary) and [netlayer](https://github.com/JesusMcCloud/netlayer) repos.




[1]: https://github.com/bisq-network/netlayer "netlayer"
[2]: https://github.com/bisq-network/tor-binary "tor-binary"
[3]: https://github.com/bisq-network/netlayer/releases "netlayer releases"
[4]: https://gitweb.torproject.org/tor.git/plain/ChangeLog "tor changelog"
[5]: https://support.torproject.org/tbb/how-to-verify-signature/ "verify tor signature"
