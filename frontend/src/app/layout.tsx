import type { Metadata } from "next";
import Link from "next/link";
import { AppProviders } from "@/components/providers/AppProviders";
import { VisitorIdentity } from "@/components/identity/VisitorIdentity";
import { appConfig } from "@/config/app";
import "./globals.css";

export const metadata: Metadata = {
  title: appConfig.name,
  description: "素材制作演示网站",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>
        <AppProviders>
          <header>
            <Link href="/">{appConfig.name}</Link>
            <nav aria-label="主导航">
              <Link href="/">模板</Link>
              <Link href="/history">我的记录</Link>
              <VisitorIdentity />
            </nav>
          </header>
          <main>{children}</main>
        </AppProviders>
      </body>
    </html>
  );
}
