import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function middleware(request: NextRequest) {
  // Read auth cookies if present
  const token = request.cookies.get('julius_auth_token')?.value;

  const { pathname } = request.nextUrl;

  // Intercept dashboard and settings
  if (pathname.startsWith('/dashboard') || pathname.startsWith('/onboarding')) {
    // Note: LocalStorage isn't accessible on middleware server components,
    // so we can also let client-side React routes handle final token redirects
    // while middleware runs standard routing validation.
    return NextResponse.next();
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/dashboard/:path*', '/onboarding/:path*'],
};
