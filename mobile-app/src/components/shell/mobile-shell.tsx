import { PropsWithChildren, useEffect, useMemo, useRef, useState } from "react";
import { Link, usePathname, useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import {
  Animated,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Feather } from "@expo/vector-icons";
import { colors, radii, shadows } from "@/theme/tokens";
import { fetchDevCurrentUser } from "@/lib/receipts";
import { fetchUserYears } from "@/lib/user-years";
import { Pill } from "@/components/ui";

type NavItem = {
  href: "/" | "/calculator" | "/profile" | "/year/create";
  label: string;
  icon: keyof typeof Feather.glyphMap;
};

const primaryNav: NavItem[] = [
  { href: "/", label: "Dashboard", icon: "grid" },
  { href: "/year/create", label: "Create New", icon: "plus-circle" },
  { href: "/calculator", label: "Calculator", icon: "percent" },
  { href: "/profile", label: "Profile", icon: "user" },
];

function isActivePath(pathname: string, href: string) {
  return href === "/" ? pathname === href : pathname.startsWith(href);
}

export function MobileShell({ children }: PropsWithChildren) {
  const pathname = usePathname();
  const router = useRouter();
  const [menuOpen, setMenuOpen] = useState(false);
  const [drawerVisible, setDrawerVisible] = useState(false);
  const drawerAnimation = useRef(new Animated.Value(0)).current;
  const userYearsQuery = useQuery({
    queryKey: ["user-years"],
    queryFn: fetchUserYears,
  });
  const devUserQuery = useQuery({
    queryKey: ["dev-current-user"],
    queryFn: fetchDevCurrentUser,
  });

  const sidebarYears = useMemo(
    () => userYearsQuery.data?.map((userYear) => userYear.year) ?? [],
    [userYearsQuery.data],
  );
  const currentEmail = devUserQuery.data?.email ?? "dev@taxrelief.local";

  useEffect(() => {
    if (menuOpen) {
      setDrawerVisible(true);
      Animated.timing(drawerAnimation, {
        toValue: 1,
        duration: 260,
        useNativeDriver: true,
      }).start();
      return;
    }

    Animated.timing(drawerAnimation, {
      toValue: 0,
      duration: 220,
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished) {
        setDrawerVisible(false);
      }
    });
  }, [drawerAnimation, menuOpen]);

  const drawerTranslateX = drawerAnimation.interpolate({
    inputRange: [0, 1],
    outputRange: [340, 0],
  });

  const backdropOpacity = drawerAnimation.interpolate({
    inputRange: [0, 1],
    outputRange: [0, 1],
  });

  return (
    <SafeAreaView style={styles.root} edges={["top"]}>
      <View style={styles.header}>
        <Link href="/" asChild>
          <Pressable style={styles.brandBlock}>
            <Text style={styles.brand}>Clair Tax</Text>
          </Pressable>
        </Link>

        <View style={styles.headerActions}>
          <View style={styles.headerMeta}>
            <Text numberOfLines={1} style={styles.email}>
              {currentEmail}
            </Text>
            <Pill tone="blue">Dev mode</Pill>
          </View>
          <Pressable
            onPress={() => setMenuOpen(true)}
            style={styles.menuButton}
            accessibilityLabel="Open menu"
          >
            <Feather name="menu" size={22} color={colors.black} />
          </Pressable>
        </View>
      </View>

      <View style={styles.content}>{children}</View>

      <View style={styles.footer}>
        <Text style={styles.footerCopy}>
          Clair Tax keeps tax work simple, clean, and easy to review.
        </Text>
        <View style={styles.footerLinks}>
          {primaryNav.map((item) => (
            <Pressable
              key={item.href}
              onPress={() => router.push(item.href)}
              style={styles.footerLink}
            >
              <Text style={styles.footerLinkText}>{item.label}</Text>
            </Pressable>
          ))}
        </View>
      </View>

      {drawerVisible ? (
        <>
          <Animated.View
            pointerEvents={menuOpen ? "auto" : "none"}
            style={[styles.backdrop, { opacity: backdropOpacity }]}
          >
            <Pressable style={StyleSheet.absoluteFill} onPress={() => setMenuOpen(false)} />
          </Animated.View>
          <Animated.View
            style={[
              styles.drawer,
              {
                transform: [{ translateX: drawerTranslateX }],
              },
            ]}
          >
            <View style={styles.drawerHeader}>
              <Text style={styles.drawerTitle}>Workspace</Text>
              <Pressable onPress={() => setMenuOpen(false)} style={styles.closeButton}>
                <Feather name="x" size={20} color={colors.black} />
              </Pressable>
            </View>

            <View style={styles.drawerGroup}>
              {primaryNav.map((item) => (
                <Pressable
                  key={item.href}
                  onPress={() => {
                    setMenuOpen(false);
                    router.push(item.href);
                  }}
                  style={[
                    styles.navLink,
                    isActivePath(pathname, item.href) ? styles.navLinkActive : null,
                  ]}
                >
                  <View style={styles.navIcon}>
                    <Feather name={item.icon} size={16} color={colors.black} />
                  </View>
                  <Text style={styles.navLabel}>{item.label}</Text>
                </Pressable>
              ))}
            </View>

            <View style={styles.divider} />

            <View style={styles.drawerGroup}>
              <Text style={styles.groupLabel}>Years</Text>
              {sidebarYears.length > 0 ? (
                sidebarYears.map((year) => (
                  <Pressable
                    key={year}
                    onPress={() => {
                      setMenuOpen(false);
                      router.push(`/year/${year}`);
                    }}
                    style={[
                      styles.yearLink,
                      pathname === `/year/${year}` ? styles.yearLinkActive : null,
                    ]}
                  >
                    <Text style={styles.yearLinkText}>{year}</Text>
                  </Pressable>
                ))
              ) : (
                <View style={styles.emptyYears}>
                  <Text style={styles.emptyYearsText}>
                    Create a year workspace to pin it here.
                  </Text>
                </View>
              )}
            </View>

            <View style={styles.drawerFooter}>
              <Text style={styles.drawerFooterText}>Send feedback</Text>
              <Text style={styles.drawerFooterText}>Privacy policy</Text>
              <Text style={styles.drawerFooterText}>Terms of Service</Text>
            </View>
          </Animated.View>
        </>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.white,
  },
  header: {
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
    backgroundColor: "rgba(255, 255, 255, 0.96)",
    paddingHorizontal: 20,
    paddingVertical: 16,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 12,
  },
  brandBlock: {
    flexShrink: 1,
  },
  brand: {
    fontSize: 34,
    lineHeight: 36,
    fontWeight: "700",
    color: colors.black,
    letterSpacing: -1.2,
  },
  headerActions: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    flexShrink: 1,
  },
  headerMeta: {
    alignItems: "flex-end",
    gap: 6,
    maxWidth: 180,
  },
  email: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: "600",
    color: colors.black,
  },
  menuButton: {
    height: 42,
    width: 42,
    borderRadius: radii.pill,
    borderWidth: 1,
    borderColor: colors.line,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.white,
  },
  content: {
    flex: 1,
    paddingTop: 12,
  },
  footer: {
    borderTopWidth: 1,
    borderTopColor: colors.line,
    paddingHorizontal: 20,
    paddingVertical: 16,
    gap: 10,
    backgroundColor: colors.white,
  },
  footerCopy: {
    fontSize: 13,
    lineHeight: 20,
    color: colors.muted,
  },
  footerLinks: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 14,
  },
  footerLink: {
    paddingVertical: 2,
  },
  footerLinkText: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: "600",
    color: colors.black,
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: colors.overlay,
  },
  drawer: {
    position: "absolute",
    top: 0,
    bottom: 0,
    right: 0,
    width: "84%",
    maxWidth: 340,
    backgroundColor: colors.white,
    paddingTop: 60,
    paddingHorizontal: 18,
    paddingBottom: 28,
    gap: 18,
    ...shadows.panel,
  },
  drawerHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  drawerTitle: {
    fontSize: 24,
    lineHeight: 30,
    fontWeight: "700",
    color: colors.black,
  },
  closeButton: {
    width: 38,
    height: 38,
    borderRadius: radii.pill,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.ice,
  },
  drawerGroup: {
    gap: 10,
  },
  navLink: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    borderRadius: radii.pill,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  navLinkActive: {
    borderWidth: 1,
    borderColor: colors.lineStrong,
    backgroundColor: "rgba(94, 155, 255, 0.2)",
  },
  navIcon: {
    width: 30,
    height: 30,
    borderRadius: radii.pill,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    alignItems: "center",
    justifyContent: "center",
  },
  navLabel: {
    fontSize: 15,
    lineHeight: 20,
    fontWeight: "600",
    color: colors.black,
  },
  divider: {
    height: 1,
    backgroundColor: colors.line,
  },
  groupLabel: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: "700",
    color: colors.black,
  },
  yearLink: {
    borderRadius: radii.card,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  yearLinkActive: {
    borderWidth: 1,
    borderColor: colors.lineStrong,
    backgroundColor: colors.white,
  },
  yearLinkText: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: "600",
    color: colors.black,
  },
  emptyYears: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderStyle: "dashed",
    borderColor: colors.line,
    padding: 14,
  },
  emptyYearsText: {
    fontSize: 14,
    lineHeight: 22,
    color: colors.muted,
  },
  drawerFooter: {
    marginTop: "auto",
    gap: 10,
    paddingTop: 16,
  },
  drawerFooterText: {
    fontSize: 14,
    lineHeight: 18,
    color: colors.muted,
  },
});
