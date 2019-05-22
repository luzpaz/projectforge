import React from 'react';
import PropTypes from 'prop-types';
import { dataPropType } from '../../../../utilities/propTypes';
import ReactSelect from './ReactSelect';
import { getServiceURL } from '../../../../utilities/rest';

class UncontrolledReactSelect extends React.Component {
    static extractDataValue(props) {
        const {
            id,
            values,
            data,
            valueProperty,
            multi,
        } = props;
        let dataValue = Object.getByString(data, id);
        if (!multi && dataValue && values && values.length && values.length > 0) {
            // For react-select it seems to be important, that the current selected element matches
            // its value of the values list.
            const valueOfArray = (typeof dataValue === 'object') ? dataValue[valueProperty] : dataValue;
            dataValue = values.find(it => it[valueProperty] === valueOfArray);
        }
        return dataValue;
    }

    constructor(props) {
        super(props);
        const dataValue = UncontrolledReactSelect.extractDataValue(props);
        this.state = {
            value: dataValue,
        };

        this.onChange = this.onChange.bind(this);
        this.loadOptions = this.loadOptions.bind(this);
    }

    onChange(newValue) {
        this.setState({ value: newValue });
        const { id, changeDataField } = this.props;
        changeDataField(id, newValue);
    }

    loadOptions(inputValue, callback) {
        const { url } = this.props;
        fetch(getServiceURL(url,
            { search: inputValue }), {
            method: 'GET',
            credentials: 'include',
            headers: {
                Accept: 'application/json',
            },
        })
            .then(response => response.json())
            .then((json) => {
                callback(json);
            })
            .catch(() => this.setState({}));
    }

    render() {
        const { value } = this.state;
        const {
            url,
            ...props
        } = this.props;
        return (
            <ReactSelect
                value={value}
                onChange={this.onChange}
                loadOptions={(url && url.length > 0) ? this.loadOptions : undefined}
                {...props}
            />
        );
    }
}

UncontrolledReactSelect.propTypes = {
    changeDataField: PropTypes.func.isRequired,
    data: dataPropType.isRequired,
    id: PropTypes.string.isRequired,
    label: PropTypes.string.isRequired,
    additionalLabel: PropTypes.string,
    values: PropTypes.arrayOf(PropTypes.object),
    valueProperty: PropTypes.string,
    labelProperty: PropTypes.string,
    multi: PropTypes.bool,
    required: PropTypes.bool,
    translations: PropTypes.shape({}).isRequired,
    url: PropTypes.string,
    getOptionLabel: PropTypes.func,
    className: PropTypes.string,
};

UncontrolledReactSelect.defaultProps = {
    additionalLabel: undefined,
    values: undefined,
    valueProperty: 'value',
    labelProperty: 'label',
    multi: false,
    required: false,
    url: undefined,
    getOptionLabel: undefined,
    className: undefined,
};
export default UncontrolledReactSelect;
