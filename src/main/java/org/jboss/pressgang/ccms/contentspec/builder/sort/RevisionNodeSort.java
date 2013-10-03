package org.jboss.pressgang.ccms.contentspec.builder.sort;

import java.util.Comparator;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RevisionNodeSort implements Comparator<Element> {

    @Override
    public int compare(Element revision1, Element revision2) {
        if (revision1 == null && revision2 == null) {
            return 0;
        }

        if (revision1 == revision2) {
            return 0;
        }

        if (revision1 == null) {
            return -1;
        }

        if (revision2 == null) {
            return 1;
        }

        final RevNumber revnumber1 = getRevnumber(revision1);
        final RevNumber revnumber2 = getRevnumber(revision2);

        if (revnumber1 == null && revnumber2 == null) {
            return 0;
        }

        if (revnumber1 == revnumber2) {
            return 0;
        }

        if (revnumber1 == null) {
            return -1;
        }

        if (revnumber2 == null) {
            return 1;
        }

        // Compare the revision
        return revnumber1.compareTo(revnumber2);
    }

    private RevNumber getRevnumber(final Element revision) {
        final NodeList revnumbers = revision.getElementsByTagName("revnumber");
        // There should only be one revnumber attribute
        if (revnumbers.getLength() > 0) {
            return new RevNumber(revnumbers.item(0).getTextContent());
        } else {
            return null;
        }
    }

    private static class RevNumber implements Comparable<RevNumber> {
        private final Version version;
        private final Version release;

        public RevNumber(final String revnumber) {
            final String[] split = revnumber.split("-", 2);
            version = new Version(split[0]);
            if (split.length > 1) {
                release = new Version(split[1]);
            } else {
                release = null;
            }
        }

        @Override
        public int compareTo(final RevNumber revnumber) {
            if (this == revnumber) {
                return 0;
            }

            if (revnumber == null) {
                return 1;
            }

            if (version == null && revnumber.version == null) {
                return 0;
            }

            if (version == revnumber.version) {
                return 0;
            }

            if (version == null) {
                return -1;
            }

            if (revnumber.version == null) {
                return 1;
            }

            if (version.equals(revnumber.version)) {
                if (release == null && revnumber.release == null) {
                    return 0;
                }

                if (release == revnumber.release) {
                    return 0;
                }

                if (release == null) {
                    return -1;
                }

                if (revnumber.release == null) {
                    return 1;
                }

                if (release.equals(revnumber.release)) {
                    return 0;
                } else {
                    return release.compareTo(revnumber.release);
                }
            } else {
                return version.compareTo(revnumber.version);
            }
        }
    }

    /**
     * This class implements the compareTo method based on http://stackoverflow.com/a/6702175/1330640
     */
    private static class Version implements Comparable<Version> {
        private final String version;

        public Version(final String version) {
            this.version = version;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null) {
                return false;
            } else if (version == null) {
                return o instanceof Version && ((Version) o).version == null;
            } else {
                return o instanceof Version && version.equals(((Version) o).version);
            }
        }

        @Override
        public int hashCode() {
            return version == null ? super.hashCode() : version.hashCode();
        }

        @Override
        public int compareTo(final Version version) {
            if (this == version) {
                return 0;
            }

            if (version == null) {
                return 1;
            }

            if (this.version == null && version.version == null) {
                return 0;
            }

            if (this.version == version.version) {
                return 0;
            }

            if (this.version == null) {
                return -1;
            }

            if (version.version == null) {
                return 1;
            }

            if (this.version.equals(version.version)) return 0; // Short circuit when you shoot for efficiency

            String[] vals1 = this.version.split("\\.");
            String[] vals2 = version.version.split("\\.");

            int i = 0;

            // Most efficient way to skip past equal version subparts
            while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) i++;

            try {
                // If we didn't reach the end,
                if (i < vals1.length && i < vals2.length) {
                    // Have to use integer comparison to avoid the "10" < "1" problem
                    return Integer.valueOf(vals2[i]).compareTo(Integer.valueOf(vals1[i]));
                }

                if (i < vals1.length) {
                    // end of version.version, check if this.version is all 0's
                    boolean allZeros = true;
                    for (int j = i; allZeros & (j < vals1.length); j++)
                        allZeros &= (Integer.parseInt(vals1[j]) == 0);
                    return allZeros ? 0 : -1;
                }

                if (i < vals2.length) {
                    // end of this.version, check if version.version is all 0's
                    boolean allZeros = true;
                    for (int j = i; allZeros & (j < vals2.length); j++)
                        allZeros &= (Integer.parseInt(vals2[j]) == 0);
                    return allZeros ? 0 : 1;
                }
            } catch (NumberFormatException e) {
                // If a number can't be parsed then ignore it and assume it's in the right position
                return 0;
            }

            return 0; // Should never happen (identical strings.)
        }
    }
}
