
<template>
  <div>
    <Highcharts :options="options" />
    <!-- <Highstock :options="options" /> -->
  </div>
</template>

<script>
export default {
  data() {
    return {};
  },
  props: {
    messagesSent: {
      type: Array,
    },
    messagesReceived: {
      type: Array,
    },
    messagesFailed: {
      type: Array,
    },
    labels: {
      type: Array,
    },
  },
  computed: {
    options: function () {
      return {
        title: {
          text: "Number of messages per day",
          x: -20, //center
        },
        // subtitle: {
        //   text: "Source: WorldClimate.com",
        //   x: -20,
        // },
        xAxis: {
          categories: this.labels,
        },
        yAxis: {
          title: {
            text: "Number Message",
          },
          plotLines: [
            {
              value: 0,
              width: 1,
              color: "#808080",
            },
          ],
        },
        tooltip: {
          valueSuffix: "",
        },
        // legend: {
        //   layout: "vertical",
        //   align: "bottom",
        //   verticalAlign: "middle",
        //   borderWidth: 0,
        // },
        colors: ["#28a745", "#007bff", "#FF0000"],
        series: [
          {
            name: "Sent messages ",
            data: this.messagesSent,
          },
          {
            name: "Received messages",
            data: this.messagesReceived,
          },
          {
            name: "Failed messages",
            data: this.messagesFailed,
          },
        ],
      };
    },
  },
};
</script>
