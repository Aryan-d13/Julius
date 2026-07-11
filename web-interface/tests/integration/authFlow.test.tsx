import React from 'react';
import { render, screen } from '@testing-library/react';
import { expect, test, vi } from 'vitest';
import LoginPage from '../../app/(auth)/login/page';
import { QueryProvider } from '../../providers/QueryProvider';

// Mock useRouter from next/navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
  }),
}));

test('displays register/login page form inputs', () => {
  render(
    <QueryProvider>
      <LoginPage />
    </QueryProvider>
  );

  expect(screen.getByPlaceholderText('operator@julius.com')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /Log In/i })).toBeInTheDocument();
});
