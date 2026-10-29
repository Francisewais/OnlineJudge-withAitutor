# 判题机：GCC 13 / OpenJDK 21 / Python 3.12 + QingdaoU/Judger 沙箱
FROM ubuntu:24.04

# 国内网络可以在 .env 里设置镜像源，例如 APT_MIRROR=mirrors.tuna.tsinghua.edu.cn
ARG APT_MIRROR=""
ARG PIP_INDEX_URL="https://pypi.org/simple"

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN if [ -n "$APT_MIRROR" ]; then \
        sed -i "s#http://archive.ubuntu.com#http://$APT_MIRROR#; s#http://security.ubuntu.com#http://$APT_MIRROR#" \
            /etc/apt/sources.list.d/ubuntu.sources; \
    fi && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        gcc g++ libc6-dev make cmake libseccomp-dev \
        openjdk-21-jdk-headless \
        python3 python3-venv && \
    rm -rf /var/lib/apt/lists/*

# 判题服务自己的依赖装在 venv 里；用户提交的 Python 代码用的是系统的 /usr/bin/python3
COPY requirements.txt /tmp/requirements.txt
RUN python3 -m venv /opt/venv && \
    /opt/venv/bin/pip install --no-cache-dir -i "$PIP_INDEX_URL" -r /tmp/requirements.txt

COPY sandbox /opt/sandbox
RUN cd /opt/sandbox && mkdir build && cd build && cmake .. && make && make install && \
    /opt/venv/bin/pip install --no-cache-dir -i "$PIP_INDEX_URL" /opt/sandbox/bindings/Python && \
    rm -rf /opt/sandbox/build /opt/sandbox/output

# 编译和运行用户代码分别使用两个没有特权的用户
RUN useradd -u 12001 -M -s /usr/sbin/nologin compiler && \
    useradd -u 12002 -M -s /usr/sbin/nologin code && \
    mkdir -p /judger/run /judger/log

COPY app /opt/judger/app
COPY tests /opt/judger/tests
WORKDIR /opt/judger
ENV PATH=/opt/venv/bin:$PATH

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
