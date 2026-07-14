/* eslint-disable @typescript-eslint/no-explicit-any */
import '@testing-library/jest-dom';
import { beforeAll, afterEach } from 'vitest';

// Stub standard browser APIs if missing in JSDOM
beforeAll(() => {
  global.localStorage = {
    getItem: (key: string) => { return (global as any).localStorageData?.[key] || null; },
    setItem: (key: string, value: string) => {
      if (!(global as any).localStorageData) { (global as any).localStorageData = {}; }
      (global as any).localStorageData[key] = value;
    },
    removeItem: (key: string) => { delete (global as any).localStorageData?.[key]; },
    clear: () => { (global as any).localStorageData = {}; },
    length: 0,
    key: () => null
  };
});

afterEach(() => {
  (global as any).localStorageData = {};
});
