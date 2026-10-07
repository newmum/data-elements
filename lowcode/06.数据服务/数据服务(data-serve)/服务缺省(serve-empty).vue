<template>
  <div class="serve-empty-container">
    <svg class="serve-empty-bg" viewBox="0 0 900 450">
      <defs>
        <linearGradient id="lineGrad" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stop-color="#DDEBFF"></stop>
          <stop offset="100%" stop-color="#165DFF"></stop>
        </linearGradient>
      </defs>
      <path
        d="M380 100 Q 420 50, 480 180"
        fill="none"
        stroke="#E5E6EB"
        stroke-width="2"
        stroke-dasharray="4,4"
      ></path>
      <path
        d="M520 225 L 680 150"
        fill="none"
        stroke="#165DFF"
        stroke-width="12"
        stroke-opacity="0.1"
        stroke-linecap="round"
      ></path>
      <path
        d="M520 225 L 680 225"
        fill="none"
        stroke="#165DFF"
        stroke-width="12"
        stroke-opacity="0.1"
        stroke-linecap="round"
      ></path>
      <path
        d="M520 225 L 680 300"
        fill="none"
        stroke="#165DFF"
        stroke-width="12"
        stroke-opacity="0.1"
        stroke-linecap="round"
      ></path>
      <path
        d="M520 225 L 680 150"
        fill="none"
        stroke="#165DFF"
        stroke-width="2"
        stroke-dasharray="3,3"
        stroke-opacity="0.4"
      ></path>
      <path
        d="M520 225 L 680 225"
        fill="none"
        stroke="#165DFF"
        stroke-width="2"
        stroke-dasharray="3,3"
        stroke-opacity="0.4"
      ></path>
      <path
        d="M520 225 L 680 300"
        fill="none"
        stroke="#165DFF"
        stroke-width="2"
        stroke-dasharray="3,3"
        stroke-opacity="0.4"
      ></path>
    </svg>
    <div class="serve-empty-table">
      <div class="table-header">
        <div class="dot red"></div>
        <div class="dot yellow"></div>
        <div class="dot green"></div>
      </div>
      <table class="data-table">
        <thead>
          <tr>
            <th></th>
            <th>
              <span class="icon-type">T</span>
              name
            </th>
            <th>
              <span class="icon-hash">#</span>
              price
            </th>
            <th>
              <span class="icon-hash">#</span>
              inventory
            </th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>1</td>
            <td class="product-name">apple juice</td>
            <td>18.0</td>
            <td>39</td>
          </tr>
          <tr>
            <td>2</td>
            <td class="product-name">milk</td>
            <td>19.0</td>
            <td>17</td>
          </tr>
          <tr>
            <td>3</td>
            <td class="product-name">ketchup</td>
            <td>10.0</td>
            <td>5</td>
          </tr>
          <tr>
            <td>4</td>
            <td class="product-name">salt</td>
            <td>22.0</td>
            <td>120</td>
          </tr>
          <tr>
            <td>5</td>
            <td class="product-name">sesame oil</td>
            <td>21.35</td>
            <td>0</td>
          </tr>
          <tr>
            <td>6</td>
            <td class="product-name">soy sauce</td>
            <td>25.0</td>
            <td>120</td>
          </tr>
          <tr>
            <td>7</td>
            <td class="product-name">seafood powder</td>
            <td>30.0</td>
            <td>15</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="serve-empty-api">
      <div class="api-circles">
        <div class="circle circle-1"></div>
        <div class="circle circle-2"></div>
        <div class="circle circle-3"></div>
        <div class="api-badge">API</div>
      </div>
    </div>
    <div class="serve-empty-features">
      <div class="feature-item">
        <div class="feature-icon smartphone">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <rect width="14" height="20" x="5" y="2" rx="2" ry="2"></rect>
            <path d="M12 18h.01"></path>
          </svg>
        </div>
        <span class="feature-label">APP</span>
      </div>
      <div class="feature-item">
        <div class="feature-icon chart">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M3 3v16a2 2 0 0 0 2 2h16"></path>
            <path d="M18 17V9"></path>
            <path d="M13 17V5"></path>
            <path d="M8 17v-3"></path>
          </svg>
        </div>
        <span class="feature-label">报表</span>
      </div>
      <div class="feature-item">
        <div class="feature-icon monitor">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <rect width="20" height="14" x="2" y="3" rx="2"></rect>
            <line x1="8" x2="16" y1="21" y2="21"></line>
            <line x1="12" x2="12" y1="17" y2="21"></line>
          </svg>
        </div>
        <span class="feature-label">大屏</span>
      </div>
      <div class="feature-item">
        <div class="feature-icon share">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <circle cx="18" cy="5" r="3"></circle>
            <circle cx="6" cy="12" r="3"></circle>
            <circle cx="18" cy="19" r="3"></circle>
            <line x1="8.59" x2="15.42" y1="13.51" y2="17.49"></line>
            <line x1="15.41" x2="8.59" y1="6.51" y2="10.49"></line>
          </svg>
        </div>
        <span class="feature-label">数据共享</span>
      </div>
    </div>
    <div class="serve-empty-decoration-top">
      <svg width="60" height="60" viewBox="0 0 24 24" fill="none">
        <path d="M21 3L1 11L10 14L21 3ZM10 14L13 21L21 3L10 14Z" fill="currentColor"></path>
      </svg>
    </div>
    <div class="serve-empty-decoration-bottom">
      <div class="dot"></div>
      <div class="dot"></div>
      <div class="dot"></div>
    </div>
  </div>
</template>

<script setup lang="ts"></script>

<style lang="scss">
.serve-empty-container {
  position: relative;
  width: 100%;
  max-width: 730px;
  height: 370px;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: scale(0.85);
}

.serve-empty-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.serve-empty-table {
  position: absolute;
  left: 8%;
  top: 50%;
  transform: translateY(-50%) rotate(-1deg);
  width: 380px;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 15px 45px rgba(0, 0, 0, 0.06);
  border: 1px solid #f2f3f5;
  overflow: hidden;
  z-index: 10;

  .table-header {
    background-color: #f8f9fa;
    padding: 10px 16px;
    display: flex;
    align-items: center;
    gap: 8px;
    border-bottom: 1px solid #f2f3f5;

    .dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;

      &.red {
        background-color: #ff6972;
      }

      &.yellow {
        background-color: #ffc53d;
      }

      &.green {
        background-color: #34d399;
      }
    }
  }

  .data-table {
    width: 100%;
    font-size: 12px;

    thead {
      background-color: #fafbfc;
      border-bottom: 1px solid #f2f3f5;

      tr {
        color: #86909c;
        text-align: left;

        th {
          padding: 10px 8px;
          font-weight: normal;

          &:first-child {
            width: 48px;
            padding-left: 20px;
          }

          .icon-type {
            color: #165dff;
          }

          .icon-hash {
            color: #34d399;
          }
        }
      }
    }

    tbody {
      color: #4e5969;

      tr {
        border-bottom: 1px solid #f2f3f5;

        &:last-child {
          border: none;
        }

        td {
          padding: 8px;

          &:first-child {
            padding-left: 20px;
            color: #c9cdd4;
            text-align: center;
          }

          &.product-name {
            color: #1d2129;
            font-weight: 500;
          }
        }
      }
    }
  }
}

.serve-empty-api {
  position: absolute;
  left: 54%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 30;

  .api-circles {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;

    .circle {
      position: absolute;
      border-radius: 50%;

      &.circle-1 {
        width: 220px;
        height: 220px;
        border: 1px solid rgba(22, 93, 255, 0.1);
      }

      &.circle-2 {
        width: 180px;
        height: 180px;
        border: 1px solid rgba(22, 93, 255, 0.2);
      }

      &.circle-3 {
        width: 140px;
        height: 140px;
        border: 1px solid rgba(22, 93, 255, 0.3);
        background-color: rgba(22, 93, 255, 0.05);
      }
    }

    .api-badge {
      width: 100px;
      height: 100px;
      background: linear-gradient(135deg, #91baff, #165dff);
      border-radius: 50%;
      box-shadow: 0 10px 30px rgba(22, 93, 255, 0.4);
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      border: 4px solid #fff;
      z-index: 10;
      font-size: 24px;
      font-weight: 900;
      letter-spacing: 0.1em;
      text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
    }
  }
}

.serve-empty-features {
  position: absolute;
  right: 8%;
  top: 50%;
  transform: translateY(-50%);
  width: 210px;
  background-color: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.04);
  border: 1px solid #f2f3f5;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  z-index: 20;

  .feature-item {
    display: flex;
    align-items: center;
    gap: 16px;

    .feature-icon {
      width: 40px;
      height: 40px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);

      &.smartphone {
        color: #5e72e4;
        background-color: #f4f6ff;
      }

      &.chart {
        color: #2dce89;
        background-color: #e9fbf3;
      }

      &.monitor {
        color: #11cdef;
        background-color: #e7fafe;
      }

      &.share {
        color: #f5365c;
        background-color: #feecf0;
      }
    }

    .feature-label {
      font-size: 15px;
      font-weight: 700;
      color: #4e5969;
    }
  }
}

.serve-empty-decoration-top {
  position: absolute;
  top: 5%;
  right: 18%;
  color: #87b4ff;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.1));
}

.serve-empty-decoration-bottom {
  position: absolute;
  bottom: 8%;
  left: 5%;
  width: 100px;
  height: 60px;
  background-color: rgba(229, 230, 235, 0.4);
  backdrop-filter: blur(4px);
  border-radius: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  opacity: 0.6;

  .dot {
    width: 8px;
    height: 8px;
    background-color: #fff;
    border-radius: 50%;
  }
}
</style>
