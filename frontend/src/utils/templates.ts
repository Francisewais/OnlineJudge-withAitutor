// 新打开一道题时编辑器里的初始代码
export const TEMPLATES: Record<string, string> = {
  c: `#include <stdio.h>

int main(void) {

    return 0;
}
`,
  cpp: `#include <bits/stdc++.h>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);

    return 0;
}
`,
  java: `import java.util.*;

// 类名必须是 Main
public class Main {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);

    }
}
`,
  python: `# 用 input() 读入一行，print() 输出
`,
}

export const LANGUAGE_FALLBACK = [
  { id: 'cpp', name: 'C++ (G++ 13, C++17)', timeFactor: 1 },
  { id: 'c', name: 'C (GCC 13, C17)', timeFactor: 1 },
  { id: 'java', name: 'Java 21', timeFactor: 2 },
  { id: 'python', name: 'Python 3.12', timeFactor: 3 },
]
